package org.vadere.util.triangulation.adaptive;

import org.vadere.util.geometry.shapes.IPoint;
import org.vadere.util.geometry.shapes.VRectangle;
import org.vadere.util.geometry.shapes.VShape;

import java.util.Collection;
import java.util.function.Function;

@FunctionalInterface
public interface IEdgeLengthFunction extends Function<IPoint,Double> {

	enum Method {
		UNIFORM, DISTMESH, DENSITY
	}

	static IEdgeLengthFunction create(final VRectangle regionBoundingBox,
	                                  final Function<IPoint, Double> densityFunc){
		return new DensityEdgeLenFunction(densityFunc, regionBoundingBox);
	}

	static IEdgeLengthFunction create(final VRectangle regionBoundingBox,
	                                  final Collection<? extends VShape> obstacles,
	                                  final IDistanceFunction distanceFunc){
		return new DistanceEdgeLenFunction(regionBoundingBox, obstacles, distanceFunc);
	}

	static IEdgeLengthFunction create(double factor) {return vertex -> factor * (1+vertex.getX()); }

	static IEdgeLengthFunction create(){
		return vertex -> 1.0;
	}
}