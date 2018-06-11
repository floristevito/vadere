package org.vadere.util.geometry.shapes;

public interface IPoint extends Cloneable {

	double getX();

	double getY();

	IPoint add(final IPoint point);

	IPoint addPrecise(final IPoint point);

	IPoint subtract(final IPoint point);

	IPoint multiply(final IPoint point);

	IPoint scalarMultiply(final double factor);

	IPoint rotate(final double radAngle);

	double scalarProduct(IPoint point);

	IPoint norm();

	IPoint norm(double len);

	IPoint normZeroSafe();

	double distance(IPoint other);

	double distance(double x, double y);

	double distanceSq(IPoint other);

	double distanceSq(double x, double y);

	double distanceToOrigin();

	default double crossProduct(IPoint point) {
		return getX() * point.getY() - point.getX() * getY();
	}

	IPoint clone();
}