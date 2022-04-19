package org.vadere.simulator.projects.dataprocessing.processor;

import org.vadere.annotation.factories.dataprocessors.DataProcessorClass;
import org.vadere.simulator.control.simulation.SimulationState;
import org.vadere.simulator.projects.dataprocessing.ProcessorManager;
import org.vadere.simulator.projects.dataprocessing.datakey.NoDataKey;
import org.vadere.state.attributes.processor.AttributesMeanAreaDensityCountingNormedProcessor;
import org.vadere.state.attributes.processor.AttributesProcessor;

import java.util.OptionalDouble;

/**
 * Saves the mean of the AreaDensityCountingNormedProcessor over the whole simulation time -> scalar output.
 * @author Floris Boendermaker
 */

@DataProcessorClass()
public class MeanAreaDensityCountingNormedProcessor extends NoDataKeyProcessor<Double> {
    private AreaDensityCountingNormedProcessor pedDensity;

    public MeanAreaDensityCountingNormedProcessor() {
        super("mean_density_counting_normed_processor");
        setAttributes(new AttributesMeanAreaDensityCountingNormedProcessor());
    }

    @Override
    protected void doUpdate(final SimulationState state) {
        //ensure that all required DataProcessors are updated.
        this.pedDensity.update(state);
    }

    @Override
    public void postLoop(final SimulationState state) {
        this.pedDensity.postLoop(state);

        OptionalDouble meanDensity = this.pedDensity.getData().values().stream().mapToDouble(Double::doubleValue).average();
        if(meanDensity.isPresent()) {
            this.putValue(NoDataKey.key(), meanDensity.getAsDouble());
        }
    }

    @Override
    public void init(final ProcessorManager manager) {
        super.init(manager);
        AttributesMeanAreaDensityCountingNormedProcessor att = (AttributesMeanAreaDensityCountingNormedProcessor) this.getAttributes();
        this.pedDensity = (AreaDensityCountingNormedProcessor) manager.getProcessor(att.getAreaDensityCountingNormedProcessorId());
    }


    @Override
    public AttributesProcessor getAttributes() {
        if (super.getAttributes() == null) {
            setAttributes(new AttributesMeanAreaDensityCountingNormedProcessor());
        }
        return super.getAttributes();
    }
}
