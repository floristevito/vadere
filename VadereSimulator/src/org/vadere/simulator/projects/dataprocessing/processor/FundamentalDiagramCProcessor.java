package org.vadere.simulator.projects.dataprocessing.processor;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.vadere.annotation.factories.dataprocessors.DataProcessorClass;
import org.vadere.simulator.control.SimulationState;
import org.vadere.simulator.projects.dataprocessing.ProcessorManager;
import org.vadere.simulator.projects.dataprocessing.datakey.TimestepKey;
import org.vadere.simulator.projects.dataprocessing.datakey.TimestepPedestrianIdKey;
import org.vadere.state.attributes.processor.AttributesFundamentalDiagramBProcessor;
import org.vadere.state.attributes.processor.AttributesFundamentalDiagramCProcessor;
import org.vadere.state.attributes.processor.AttributesProcessor;
import org.vadere.state.scenario.MeasurementArea;
import org.vadere.util.factory.processors.ProcessorFlag;
import org.vadere.util.geometry.shapes.VRectangle;

import java.util.List;

/**
 * <p>This processor computes the fundamental diagram by computing at a certain time the
 * <tt>density</tt> defined by the number of pedestrians contained in the <tt>measurementArea</tt>
 * divided by the area of <tt>measurementArea</tt> and the <tt>velocity</tt> which is defined by
 * the sum of pedestrian velocities contained in the <tt>measurementArea</tt> divided by the number
 * of pedestrians contained in <tt>measurementArea</tt>. This is the so called classical method.</p>
 *
 * <p>For more details see zhang-2011 (doi:10.1088/1742-5468/2011/06/P06004) Method C.</p>
 *
 * @author Benedikt Zoennchen
 */
@DataProcessorClass(processorFlags = {ProcessorFlag.needMeasurementArea})
public class FundamentalDiagramCProcessor extends AreaDataProcessor<Pair<Double, Double>>  {

	private MeasurementArea measurementArea;

	private APedestrianVelocityProcessor pedestrianVelocityProcessor;

	public FundamentalDiagramCProcessor() {
		super("velocity", "density");
	}

	@Override
	public void init(final ProcessorManager manager) {
		super.init(manager);
		AttributesFundamentalDiagramCProcessor att = (AttributesFundamentalDiagramCProcessor) this.getAttributes();
		measurementArea = manager.getMeasurementArea(att.getMeasurementAreaId());
		pedestrianVelocityProcessor = (APedestrianVelocityProcessor) manager.getProcessor(att.getPedestrianVelocityProcessorId());
		if (measurementArea == null)
			throw new RuntimeException(String.format("MeasurementArea with index %d does not exist.", att.getMeasurementAreaId()));
		if (!measurementArea.isRectangular())
			throw new RuntimeException("DataProcessor only supports Rectangular measurement areas.");
	}

	@Override
	public AttributesProcessor getAttributes() {
		if (super.getAttributes() == null) {
			setAttributes(new AttributesFundamentalDiagramCProcessor());
		}
		return super.getAttributes();
	}

	@Override
	public void preLoop(SimulationState state) {
		super.preLoop(state);
	}

	@Override
	protected void doUpdate(SimulationState state) {
		pedestrianVelocityProcessor.update(state);
		long N = state.getTopography().getPedestrianDynamicElements().getElements()
				.stream()
				.filter(pedestrian -> measurementArea.asVRectangle().contains(pedestrian.getPosition()))
				.count();
		double velocity = state.getTopography().getPedestrianDynamicElements().getElements()
				.stream()
				.filter(pedestrian -> measurementArea.asVRectangle().contains(pedestrian.getPosition()))
				.mapToDouble(pedestrian ->
						//pedestrian.getVelocity().getLength()
						pedestrianVelocityProcessor.getValue(new TimestepPedestrianIdKey(state.getStep(), pedestrian.getId()))
				)
				.sum();

		if(N == 0) {
			velocity = 0.0;
		}
		else {
			velocity /= N;
		}

		double density = N / measurementArea.asVRectangle().getArea();

		putValue(new TimestepKey(state.getStep()), Pair.of(velocity, density));
	}

	@Override
	public String[] toStrings(@NotNull final TimestepKey key) {
		return new String[]{ Double.toString(getValue(key).getLeft()), Double.toString(getValue(key).getRight()) };
	}

	@Override
	public boolean sanityCheck(Object o) {
		List<MeasurementArea> data = (List<MeasurementArea>) o;
		AttributesFundamentalDiagramCProcessor att = (AttributesFundamentalDiagramCProcessor) this.getAttributes();

		boolean match = data.stream().map(MeasurementArea::getId).anyMatch(id -> id == att.getMeasurementAreaId());
		return match;
	}
}
