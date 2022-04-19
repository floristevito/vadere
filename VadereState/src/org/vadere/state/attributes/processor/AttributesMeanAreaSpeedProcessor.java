package org.vadere.state.attributes.processor;

/**
 * @author Floris Boendermaker
 *
 */

public class AttributesMeanAreaSpeedProcessor extends AttributesProcessor {
    private int areaSpeedProcessorId;

    public int getAreaSpeedProcessorId() {
        return this.areaSpeedProcessorId;
    }

    public void setAreaProcessorId(int meanAreaSpeedProcessorId) {
        checkSealed();
        this.areaSpeedProcessorId = meanAreaSpeedProcessorId;
    }
}