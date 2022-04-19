package org.vadere.simulator.projects.dataprocessing.processor;

import org.vadere.annotation.factories.dataprocessors.DataProcessorClass;
import org.vadere.simulator.control.simulation.SimulationState;
import org.vadere.simulator.projects.dataprocessing.ProcessorManager;
import org.vadere.simulator.projects.dataprocessing.datakey.NoDataKey;
import org.vadere.state.attributes.processor.AttributesMaxAreaDensityCountingNormedProcessor;
import org.vadere.state.attributes.processor.AttributesProcessor;

import java.util.OptionalDouble;

/**
 * Saves the max of the AreaDensityCountingNormedProcessor over the whole simulation time -> scalar output.
 * @author Floris Boendermaker
 */

@DataProcessorClass()
public class MaxAreaDensityCountingNormedProcessor extends NoDataKeyProcessor<Double> {
    private AreaDensityCountingNormedProcessor pedDensity;

    public MaxAreaDensityCountingNormedProcessor() {
        super("max_density_counting_normed_processor");
        setAttributes(new AttributesMaxAreaDensityCountingNormedProcessor());
    }

    @Override
    protected void doUpdate(final SimulationState state) {
        //ensure that all required DataProcessors are updated.
        this.pedDensity.update(state);
    }

    @Override
    public void postLoop(final SimulationState state) {
        this.pedDensity.postLoop(state);

        OptionalDouble maxDensity = this.pedDensity.getData().values().stream().mapToDouble(Double::doubleValue).max();
        if(maxDensity.isPresent()) {
            this.putValue(NoDataKey.key(), maxDensity.getAsDouble());
        }
    }

    @Override
    public void init(final ProcessorManager manager) {
        super.init(manager);
        AttributesMaxAreaDensityCountingNormedProcessor att = (AttributesMaxAreaDensityCountingNormedProcessor) this.getAttributes();
        this.pedDensity = (AreaDensityCountingNormedProcessor) manager.getProcessor(att.getAreaDensityCountingNormedProcessorId());
    }


    @Override
    public AttributesProcessor getAttributes() {
        if (super.getAttributes() == null) {
            setAttributes(new AttributesMaxAreaDensityCountingNormedProcessor());
        }
        return super.getAttributes();
    }
}
