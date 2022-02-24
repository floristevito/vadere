package org.vadere.state.attributes.models;

import org.vadere.annotation.factories.attributes.ModelAttributeClass;

import org.vadere.state.attributes.models.infection.AttributesAirTransmissionModelAerosolCloud;
import org.vadere.state.attributes.models.infection.AttributesAirTransmissionModelDroplets;
import org.vadere.state.attributes.models.infection.AttributesExposureModel;
import org.vadere.state.scenario.*;

/**
 * Attributes related to the corresponding exposure model. They define properties of {@link Pedestrian}s,
 * {@link AerosolCloud}s, and {@link Droplets} that are equal for all instances of each class.
 */
@ModelAttributeClass
public class AttributesAirTransmissionModel extends AttributesExposureModel {

	/**
	 * Equals 1/(pedestrians' average breathing rate).
	 * Unit: seconds
	 */
	private double pedestrianRespiratoryCyclePeriod;

	/**
	 * Defines whether aerosol clouds are considered in the exposure model (true) or not (false).
	 */
	private boolean aerosolCloudsActive;


	private AttributesAirTransmissionModelAerosolCloud aerosolCloudParameters;

	/**
	 * Defines whether droplets are considered in the exposure model (true) or not (false).
	 */
	private boolean dropletsActive;
	private AttributesAirTransmissionModelDroplets dropletParameters;


	public AttributesAirTransmissionModel() {
		super();

		this.pedestrianRespiratoryCyclePeriod = 4;

		this.aerosolCloudsActive = false;
		this.aerosolCloudParameters = new AttributesAirTransmissionModelAerosolCloud();

		this.dropletsActive = false;
		this.dropletParameters = new AttributesAirTransmissionModelDroplets();
	}

	// Getter

	public double getPedestrianRespiratoryCyclePeriod() {
		return pedestrianRespiratoryCyclePeriod;
	}

	public boolean isAerosolCloudsActive() {
		return aerosolCloudsActive;
	}

	public double getAerosolCloudInitialPathogenLoad() {
		return aerosolCloudParameters.getInitialPathogenLoad();
	}

	public double getAerosolCloudHalfLife() {
		return aerosolCloudParameters.getHalfLife();
	}

	public double getAerosolCloudInitialRadius() {
		return aerosolCloudParameters.getInitialRadius();
	}

	public double getAerosolCloudAirDispersionFactor() {
		return aerosolCloudParameters.getAirDispersionFactor();
	}

	public double getAerosolCloudPedestrianDispersionWeight() {
		return aerosolCloudParameters.getPedestrianDispersionWeight();
	}

	public double getAerosolCloudAbsorptionRate() {
		return aerosolCloudParameters.getAbsorptionRate();
	}

	public boolean isDropletsActive() {
		return dropletsActive;
	}

	public double getDropletsEmissionFrequency() {
		return dropletParameters.getEmissionFrequency();
	}

	public double getDropletsDistanceOfSpread() {
		return dropletParameters.getDistanceOfSpread();
	}

	public double getDropletsAngleOfSpreadInDeg() {
		return dropletParameters.getAngleOfSpreadInDeg();
	}

	public double getDropletsLifeTime() {
		return dropletParameters.getLifeTime();
	}

	public double getDropletsPathogenLoad() {
		return dropletParameters.getPathogenLoad();
	}

	public double getDropletsAbsorptionRate() {
		return dropletParameters.getAbsorptionRate();
	}
}
