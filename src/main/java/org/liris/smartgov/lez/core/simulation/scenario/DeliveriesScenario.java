package org.liris.smartgov.lez.core.simulation.scenario;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.liris.smartgov.lez.cli.tools.PoliticRun;
import org.liris.smartgov.lez.core.agent.driver.DeliveryDriverAgent;
import org.liris.smartgov.lez.core.agent.driver.DriverBody;
import org.liris.smartgov.lez.core.agent.driver.PrivateDriverAgent;
import org.liris.smartgov.lez.core.agent.driver.behavior.DeliveryDriverBehavior;
import org.liris.smartgov.lez.core.agent.driver.behavior.DriverBehavior;
import org.liris.smartgov.lez.core.agent.driver.behavior.PrivateDriverBehavior;
import org.liris.smartgov.lez.core.agent.driver.behavior.WorkerBehavior;
import org.liris.smartgov.lez.core.agent.driver.behavior.WorkerHomeAtNoonBehavior;
import org.liris.smartgov.lez.core.agent.driver.behavior.WorkerOneActivityBehavior;
import org.liris.smartgov.lez.core.agent.driver.personality.Personality;
import org.liris.smartgov.lez.core.agent.driver.behavior.LezBehavior;
import org.liris.smartgov.lez.core.agent.driver.vehicle.Vehicle;
import org.liris.smartgov.lez.core.agent.establishment.Establishment;
import org.liris.smartgov.lez.core.agent.establishment.Round;
import org.liris.smartgov.lez.core.agent.establishment.ST8;
import org.liris.smartgov.lez.core.agent.establishment.preprocess.LezPreprocessor;
import org.liris.smartgov.lez.core.copert.fields.EuroNorm;
import org.liris.smartgov.lez.core.copert.tableParser.CopertParser;
import org.liris.smartgov.lez.core.environment.LezContext;
import org.liris.smartgov.lez.core.environment.graph.PollutableOsmArcFactory;
import org.liris.smartgov.lez.core.environment.lez.Environment;
import org.liris.smartgov.lez.input.establishment.EstablishmentLoader;
import org.liris.smartgov.lez.politic.PoliticalCreator;
import org.liris.smartgov.simulator.SmartGov;
import org.liris.smartgov.simulator.core.agent.core.Agent;
import org.liris.smartgov.simulator.core.environment.SmartGovContext;
import org.liris.smartgov.simulator.core.environment.graph.Node;
import org.liris.smartgov.simulator.urban.geo.environment.graph.GeoStrTree;
import org.liris.smartgov.simulator.urban.geo.utils.lonLat.LonLat;
import org.liris.smartgov.simulator.urban.osm.agent.OsmAgent;
import org.liris.smartgov.simulator.urban.osm.environment.graph.OsmNode;
import org.liris.smartgov.simulator.urban.osm.environment.graph.Road;
import org.liris.smartgov.simulator.urban.osm.environment.graph.tags.Highway;
import org.liris.smartgov.simulator.urban.osm.utils.OsmArcsBuilder;

/**
 * Scenario used to model deliveries between establishments with
 * pollutant emissions.
 *
 */
public class DeliveriesScenario extends PollutionScenario {


	/**
	 * LezDeliveries
	 */
	public static final String name = "LezDeliveries";
	private Map<String, Establishment> establishments;
	
	
	
	/**
	 * Establishments won't be delivered in those highways, even
	 * if they can be used in trajectories.
	 * 
	 * <ul>
	 * <li> {@link Highway#MOTORWAY} </li>
	 * <li> {@link Highway#MOTORWAY_LINK} </li>
	 * <li> {@link Highway#TRUNK} </li>
	 * <li> {@link Highway#TRUNK_LINK} </li>
	 * <li> {@link Highway#LIVING_STREET} </li>
	 * <li> {@link Highway#SERVICE} </li>
	 * </ul>
	 * 
	 * <p>
	 * Living streets and service ways are not used, because their
	 * usage to often bring situations with dead ends.
	 * </p>
	 */
	public static final Highway[] forbiddenClosestNodeHighways = {
			Highway.MOTORWAY,
			Highway.MOTORWAY_LINK,
			Highway.TRUNK,
			Highway.TRUNK_LINK,
			Highway.LIVING_STREET,
			Highway.SERVICE
	};
	
	/**
	 * DeliveriesScenario constructor.
	 * 
	 * @param lez LEZ used in this scenario
	 */
	public DeliveriesScenario(Environment environment) {
		super(environment);
	}
	
	@Override
	public void reloadWorld(SmartGovContext context) {
		for (Agent<?> agent : buildAgents(context, true)) {
			context.agents.put(agent.getId(), agent);
		}
	}
	
	public Map<String, Establishment> loadEstablishments(SmartGovContext context, CopertParser parser) {
		
		int deadEnds = 0;
		for(Node node : context.nodes.values()) {
			if(node.getOutgoingArcs().isEmpty() || node.getIncomingArcs().isEmpty()) {
				deadEnds++;
				Road road = ((OsmNode) node).getRoad();
				PoliticRun.logger.debug("Dead end found on node " + node.getId() + ", road " + road.getId());
			}
		}
		PoliticRun.logger.info(deadEnds + " dead ends found.");
		
		OsmArcsBuilder.fixDeadEnds((LezContext) context, new PollutableOsmArcFactory(getEnvironment()));
		
		Map<String, Establishment> establishments = null;
		try {
			establishments = 
					EstablishmentLoader.loadEstablishments(
							context.getFileLoader().load("establishments"),
							context.getFileLoader().load("fleet_profiles"),
							parser,
							PollutionScenario.random
							);
		} catch (IOException e) {
			e.printStackTrace();
		}
		((LezContext) context).setEstablishments(establishments);
		
		Map<String, OsmNode> geoNodes = new HashMap<>();
		for (String id : context.nodes.keySet()) {
			OsmNode node = (OsmNode) context.nodes.get(id);
			if(!Arrays.asList(forbiddenClosestNodeHighways).contains(node.getRoad().getHighway()))
					geoNodes.put(id, node);
		}
		GeoStrTree kdTree = new GeoStrTree(geoNodes);
		
		PoliticRun.logger.info("Searching for the closest node of each establishment.");
		for (Establishment establishment : establishments.values()) {
			establishment.setClosestOsmNode((OsmNode) kdTree.getNearestNodeFrom(
					new LonLat().project(establishment.getLocation())
					)
				);
			//we create personalities of agents
			for (String vehicleId : establishment.getRounds().keySet()) {
				establishment.setPersonality(vehicleId, new Personality(establishment.getActivity(), vehicleId));
			}
		}
		
		PoliticalCreator.createPoliticalLayer(getEnvironment());
		
		return establishments;
	}
	

	public Collection<? extends Agent<?>> buildAgents(SmartGovContext context, boolean reload) {
		CopertParser parser = loadParser(context);
		
		if (!reload) {
			establishments = loadEstablishments(context, parser);
		} else {
			for (Establishment establishment: establishments.values()) {
				establishment.resetFleet();
			}
		}
		
		PoliticRun.logger.info("Applying lez...");
		LezPreprocessor preprocessor = new LezPreprocessor(getEnvironment(), parser);
		int totalVehiclesReplaced = 0;
		int totalMobilityChanged = 0;
		int totalFrauds = 0;
		
		for( Establishment establishment : establishments.values() ) {
			Map<String, Integer> indicators =  preprocessor.preprocess(establishment);
			totalVehiclesReplaced += indicators.get("Replaced");
			totalMobilityChanged += indicators.get("Mobility");
			totalFrauds += indicators.get("Fraud");
		}
		
		PoliticRun.logger.info("[LEZ] Total number of vehicles replaced : " + totalVehiclesReplaced);
		PoliticRun.logger.info("[LEZ] Total number of mobility changed: " + totalMobilityChanged);
		PoliticRun.logger.info("[LEZ] Total number of agents who chose to fraud : " + totalFrauds);
		
		
		int agentId = 0;
		Collection<OsmAgent> agents = new ArrayList<>();
		Collection<BuildAgentThread> threads = new ArrayList<>();
		
		Random random = new Random(111111);
		
		for (Establishment establishment : establishments.values()) {
			for (String vehicleId :  establishment.getRounds().keySet()) {
				if ( establishment.getFleet().get(vehicleId) != null ) {
					//if he didn't change mobility
					BuildAgentThread thread = new BuildAgentThread(agentId++, vehicleId, establishment, (LezContext) context, random);
					threads.add(thread);
					thread.start();
				}
			}
		}
		
		for(BuildAgentThread thread : threads) {
			try {
				thread.join();
				agents.add(thread.getBuiltAgent());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		return agents;
	}
	
	private static class BuildAgentThread extends Thread {
		
		private int agentId;
		private String vehicleId;
		private Establishment establishment;
		private LezContext context;
		
		private OsmAgent builtAgent;
		private DriverBehavior builtBehavior;
		private Random random;
		
		public BuildAgentThread(int agentId, String vehicleId, Establishment establishment, LezContext context, Random random) {
			super();
			this.agentId = agentId;
			this.vehicleId = vehicleId;
			this.establishment = establishment;
			this.context = context;
			this.random = random;
		}

		public void run() {
			DriverBody driver = new DriverBody(establishment.getFleet().get(vehicleId));
			
			if ( establishment.getActivity() != ST8.PRIVATE_HABITATION) {
				//if it is a delivery establishment
				builtBehavior
				= new DeliveryDriverBehavior(
						driver,
						establishment.getRounds().get(vehicleId),
						establishment.getPersonalities().get(vehicleId),
						context
						);
				builtAgent = new DeliveryDriverAgent(String.valueOf(agentId),
						driver, (DeliveryDriverBehavior)builtBehavior);
			}
			else {
				//Private agent
				if (establishment.getRounds().get(vehicleId).getEstablishments().size() < 2) {
					//if only one establishment, might be either a worker (2/3) or a worker home at noon (1/3)
					if (random.nextInt(4) == 0) {
						builtBehavior = new WorkerHomeAtNoonBehavior(
								driver,
								establishment.getRounds().get(vehicleId),
								establishment.getPersonalities().get(vehicleId),
								context,
								random);
					}
					else {
						builtBehavior = new WorkerBehavior(
								driver,
								establishment.getRounds().get(vehicleId),
								establishment.getPersonalities().get(vehicleId),
								context, 
								random);
					}
				} else {
					builtBehavior = new WorkerOneActivityBehavior(
							driver,
							establishment.getRounds().get(vehicleId),
							establishment.getPersonalities().get(vehicleId),
							context, 
							random);
				}

				try {builtAgent = new PrivateDriverAgent(String.valueOf(agentId),
						driver, (PrivateDriverBehavior)builtBehavior);}
				catch (IllegalArgumentException e) {
					System.err.println("Agent from establishment " + establishment.getId() + " could not create his path");
					e.printStackTrace();
				}
			}
			

			
			

			builtBehavior.addRoundDepartureListener((event) -> {
				/*PoliticRun.logger.info(
				"[" + SmartGov.getRuntime().getClock().getHour()
				+ ":" + SmartGov.getRuntime().getClock().getMinutes() + "]"
				+ "Agent " + builtAgent.getId()
				+ " begins round for [" + establishment.getId() + "] "
				+ establishment.getName());*/
			});
				
			builtBehavior.addRoundEndListener((event) -> {
				/*PoliticRun.logger.info(
					"[" + SmartGov.getRuntime().getClock().getHour()
					+ ":" + SmartGov.getRuntime().getClock().getMinutes() + "]"
					+ "Agent " + builtAgent.getId()
					+ " ended round for [" + establishment.getId() + "] "
					+ establishment.getName()
					);*/
				context.ongoingRounds.remove(builtAgent.getId());
				PoliticRun.logger.info("Rounds still ongoing : " + context.ongoingRounds.size());
				if(context.ongoingRounds.isEmpty()) {
					SmartGov.getRuntime().stop();
				}
			});
		}
		
		/*
		 * Listeners are initialized there from the main thread to avoid
		 * concurrent modifications errors.
		 * Rounds are also added to the context there, for the same reasons.
		 */
		public OsmAgent getBuiltAgent() {
			builtBehavior.setUpListeners();
			context.ongoingRounds.put(builtAgent.getId(), builtBehavior.getRound());
			return builtAgent;
		}
		
	}
	
	public static class NoLezDeliveries extends DeliveriesScenario {
		public static final String name = "NoLezDeliveries";
		
		public NoLezDeliveries() {
			super(Environment.none());
		}
	}

	@Override
	public Collection<? extends Agent<?>> buildAgents(SmartGovContext context) {
		return buildAgents(context, false);
	}

}
