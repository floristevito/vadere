package org.vadere.simulator.projects.dataprocessing.processor;

import org.mockito.Mockito;
import org.vadere.simulator.projects.dataprocessing.writer.VadereStringWriter;
import org.vadere.simulator.projects.dataprocessing.datakey.TimestepPedestrianIdKey;
import org.vadere.simulator.projects.dataprocessing.writer.VadereStringWriterFactory;
import org.vadere.simulator.projects.dataprocessing.writer.VadereWriterFactory;
import org.vadere.util.geometry.shapes.VPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class PedestrianOverlapProcessorTestEnv extends ProcessorTestEnv<TimestepPedestrianIdKey, Integer> {

	PedestrianOverlapProcessorTestEnv(){
		testedProcessor = processorFactory.createDataProcessor(PedestrianOverlapProcessor.class);
		testedProcessor.setId(nextProcessorId());

		outputFile = outputFileFactory.createDefaultOutputfileByDataKey(
				TimestepPedestrianIdKey.class,
				testedProcessor.getId());
		outputFile.setVadereWriterFactory(VadereWriterFactory.getStringWriterFactory());
	}

	@Override
	public void loadDefaultSimulationStateMocks() {
		addSimState(new SimulationStateMock(1) {
			@Override
			public void mockIt() {
				Map<Integer, VPoint> pedPosMap = new HashMap<>();
				pedPosMap.put(1, new VPoint(1.0, 1.0));
				pedPosMap.put(2, new VPoint(1.5, 1.5));
				pedPosMap.put(3, new VPoint(1.5, 1.0));
				pedPosMap.put(4, new VPoint(1.0, 1.5));
				Mockito.when(state.getPedestrianPositionMap()).thenReturn(pedPosMap);

				int step = state.getStep();
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 1), 0);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 2), 0);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 3), 0);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 4), 0);
			}
		});

		addSimState(new SimulationStateMock(2) {
			@Override
			public void mockIt() {
				Map<Integer, VPoint> pedPosMap = new HashMap<>();
				pedPosMap.put(1, new VPoint(1.0, 1.0));
				pedPosMap.put(2, new VPoint(1.5, 1.5));
				pedPosMap.put(3, new VPoint(1.2, 1.0));
				pedPosMap.put(4, new VPoint(1.0, 1.5));
				pedPosMap.put(5, new VPoint(0.8, 0.8));
				Mockito.when(state.getPedestrianPositionMap()).thenReturn(pedPosMap);

				int step = state.getStep();
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 1), 2);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 2), 0);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 3), 1);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 4), 0);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 5), 1);
			}
		});

		addSimState(new SimulationStateMock(3) {
			@Override
			public void mockIt() {
				Map<Integer, VPoint> pedPosMap = new HashMap<>();
				pedPosMap.put(1, new VPoint(1.0, 1.0));
				pedPosMap.put(2, new VPoint(1.0, 1.0));
				pedPosMap.put(3, new VPoint(1.0, 1.0));
				pedPosMap.put(4, new VPoint(1.0, 1.0));
				Mockito.when(state.getPedestrianPositionMap()).thenReturn(pedPosMap);

				int step = state.getStep();
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 1), 3);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 2), 3);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 3), 3);
				addToExpectedOutput(new TimestepPedestrianIdKey(step, 4), 3);
			}
		});
	}

	@Override
	List<String> getExpectedOutputAsList() {
		List<String> outputList = new ArrayList<>();
		expectedOutput.entrySet()
				.stream()
				.sorted(Comparator.comparing(Map.Entry::getKey))
				.forEach(e -> {
					StringJoiner js = new StringJoiner(getDelimiter());
					js.add(Integer.toString(e.getKey().getTimestep()))
							.add(Integer.toString(e.getKey().getPedestrianId()))
							.add(Integer.toString(e.getValue()));
					outputList.add(js.toString());
				});
		return outputList;
	}
}
