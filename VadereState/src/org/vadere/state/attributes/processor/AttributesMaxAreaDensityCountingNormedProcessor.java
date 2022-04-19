package org.vadere.state.attributes.processor;

/**
 * @author Floris Boendermaker
 *
 */

public class AttributesMaxAreaDensityCountingNormedProcessor extends AttributesProcessor {
    private int areaDensityCountingNormedProcessorId;

    public int getAreaDensityCountingNormedProcessorId() {
        return this.areaDensityCountingNormedProcessorId;
    }

    public void setAreaDensityCountingNormedProcessorId(int maxAreaDensityCountingNormedProcessorId) {
        checkSealed();
        this.areaDensityCountingNormedProcessorId = maxAreaDensityCountingNormedProcessorId;
    }
}