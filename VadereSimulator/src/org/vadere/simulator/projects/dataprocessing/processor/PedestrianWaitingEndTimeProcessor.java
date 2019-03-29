package org.vadere.simulator.projects.dataprocessing.processor;

import org.vadere.annotation.factories.dataprocessors.DataProcessorClass;
import org.vadere.simulator.control.SimulationState;
import org.vadere.simulator.projects.dataprocessing.ProcessorManager;
import org.vadere.simulator.projects.dataprocessing.datakey.PedestrianIdKey;
import org.vadere.state.attributes.processor.AttributesCrossingTimeProcessor;
import org.vadere.state.attributes.processor.AttributesPedestrianWaitingEndTimeProcessor;
import org.vadere.state.attributes.processor.AttributesProcessor;
import org.vadere.state.scenario.MeasurementArea;
import org.vadere.state.scenario.Pedestrian;
import org.vadere.util.factory.processors.ProcessorFlag;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VRectangle;

import java.util.Collection;
import java.util.List;

/**
 * @author Mario Teixeira Parente
 */
@DataProcessorClass(processorFlags = {ProcessorFlag.needMeasurementArea})
public class PedestrianWaitingEndTimeProcessor extends DataProcessor<PedestrianIdKey, Double> {
	private MeasurementArea waitingArea;

	public PedestrianWaitingEndTimeProcessor() {
		super("waitingEndTime");
		setAttributes(new AttributesPedestrianWaitingEndTimeProcessor());
	}

	@Override
	protected void doUpdate(final SimulationState state) {
		Collection<Pedestrian> peds = state.getTopography().getElements(Pedestrian.class);
		for (Pedestrian p : peds) {
			int pedId = p.getId();
			VPoint pos = p.getPosition();

			if (this.waitingArea.asVRectangle().contains(pos)) {
				PedestrianIdKey key = new PedestrianIdKey(pedId);
				this.putValue(key, state.getSimTimeInSec());
			}
		}
	}

	@Override
	public void init(final ProcessorManager manager) {
		super.init(manager);
		AttributesPedestrianWaitingEndTimeProcessor att = (AttributesPedestrianWaitingEndTimeProcessor) this.getAttributes();
		this.waitingArea = manager.getMeasurementArea(att.getWaitingAreaId());
		if (waitingArea == null )
			throw new RuntimeException(String.format("MeasurementArea with index %d does not exist.", att.getWaitingAreaId()));
		if (!waitingArea.isRectangular())
			throw new RuntimeException("DataProcessor and IntegralVoronoiAlgorithm only supports Rectangular measurement areas.");

	}

	@Override
	public AttributesProcessor getAttributes() {
		if (super.getAttributes() == null) {
			setAttributes(new AttributesPedestrianWaitingEndTimeProcessor());
		}

		return super.getAttributes();
	}

	@Override
	public boolean sanityCheck(Object o) {
		List<MeasurementArea> data = (List<MeasurementArea>) o;
		AttributesPedestrianWaitingEndTimeProcessor att = (AttributesPedestrianWaitingEndTimeProcessor) this.getAttributes();

		boolean match1 = data.stream().map(MeasurementArea::getId).anyMatch(id -> id == att.getWaitingAreaId());

		return match1;
	}
}
