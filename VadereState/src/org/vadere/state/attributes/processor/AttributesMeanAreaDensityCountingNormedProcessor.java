package org.vadere.state.attributes.processor;

/**
 * @author Floris Boendermaker
 *
 */

public class AttributesMeanAreaDensityCountingNormedProcessor extends AttributesProcessor {
    private int areaDensityCountingNormedProcessorId;

    public int getAreaDensityCountingNormedProcessorId() {
        return this.areaDensityCountingNormedProcessorId;
    }

    public void setAreaDensityCountingNormedProcessorId(int meanAreaDensityMeanCountingNormedProcessorId) {
        checkSealed();
        this.areaDensityCountingNormedProcessorId = meanAreaDensityMeanCountingNormedProcessorId;
    }
}