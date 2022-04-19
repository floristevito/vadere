package org.vadere.simulator.projects.dataprocessing.processor;

import org.vadere.annotation.factories.dataprocessors.DataProcessorClass;
import org.vadere.simulator.control.simulation.SimulationState;
import org.vadere.simulator.projects.dataprocessing.ProcessorManager;
import org.vadere.simulator.projects.dataprocessing.datakey.NoDataKey;
import org.vadere.state.attributes.processor.AttributesMeanAreaSpeedProcessor;
import org.vadere.state.attributes.processor.AttributesProcessor;

import java.util.OptionalDouble;

/**
 * Saves the mean of the AreaSpeedProcessor over the whole simulation time -> scalar output.
 * @author Floris Boendermaker
 */

@DataProcessorClass()
public class MeanAreaSpeedProcessor extends NoDataKeyProcessor<Double> {
    private AreaSpeedProcessor pedSpeed;

    public MeanAreaSpeedProcessor () {
        super("mean_area_speed_processor");
        setAttributes(new AttributesMeanAreaSpeedProcessor());
    }

    @Override
    protected void doUpdate(final SimulationState state) {
        //ensure that all required DataProcessors are updated.
        this.pedSpeed.update(state);
    }

    @Override
    public void postLoop(final SimulationState state) {
        this.pedSpeed.postLoop(state);

        OptionalDouble meanSpeed = this.pedSpeed.getData().values().stream().mapToDouble(Double::doubleValue).filter(d -> !Double.isNaN(d)).average();
        if(meanSpeed.isPresent()) {
            this.putValue(NoDataKey.key(), meanSpeed.getAsDouble());
        }
    }

    @Override
    public void init(final ProcessorManager manager) {
        super.init(manager);
        AttributesMeanAreaSpeedProcessor att = (AttributesMeanAreaSpeedProcessor) this.getAttributes();
        this.pedSpeed = (AreaSpeedProcessor) manager.getProcessor(att.getAreaSpeedProcessorId());
    }


    @Override
    public AttributesProcessor getAttributes() {
        if (super.getAttributes() == null) {
            setAttributes(new AttributesMeanAreaSpeedProcessor());
        }
        return super.getAttributes();
    }
}
