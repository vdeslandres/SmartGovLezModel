package org.liris.smartgov.lez.core.agent.driver.behavior;

import java.util.Random;

import org.liris.smartgov.lez.cli.tools.Run;
import org.liris.smartgov.lez.core.agent.driver.DriverBody;
import org.liris.smartgov.lez.core.agent.driver.personality.Personality;
import org.liris.smartgov.lez.core.agent.establishment.Round;
import org.liris.smartgov.lez.core.simulation.ExtendedDate;
import org.liris.smartgov.lez.core.simulation.files.FilePath;
import org.liris.smartgov.lez.core.simulation.files.FilesManagement;
import org.liris.smartgov.simulator.SmartGov;
import org.liris.smartgov.simulator.core.agent.moving.behavior.MoverAction;
import org.liris.smartgov.simulator.core.environment.SmartGovContext;
import org.liris.smartgov.simulator.core.simulation.time.Date;
import org.liris.smartgov.simulator.core.simulation.time.DelayedActionHandler;
import org.liris.smartgov.simulator.core.simulation.time.WeekDay;
/**
 * Behavior of worker private agent.
 * His behavior is : 
 * <ul>
 * 	<li> Leaves his origin establishment between 7am and 9am and to go to work.</li>
 * 	<li> Leaves his work between 4pm and 7pm to go home.</li>
 * </ul>
 * @author alban
 *
 */
public class WorkerBehavior extends PrivateDriverBehavior {
	
	private int position;
	private int journeyTime;
	private Date[] departures;
	
	/**
	 * WorkerBehavior constructor.
	 *
	 * @param agentBody associated body
	 * @param round round to perform
	 * @param personality personality associated to the agent
	 * @param context currentContext
	 * @param random an instantiated random
	 */
	public WorkerBehavior(
			DriverBody agentBody,
			Round round,
			Personality personality,
			SmartGovContext context,
			Random random
			) {
		super(agentBody,
				round,
				personality,
				context,
				random);
		
		departures = new Date[2];
		journeyTime = 0;
		
		if (round.getEstablishments() == null || round.getEstablishments().size() == 0) {
			throw new IllegalArgumentException("This behavior needs one establishment in its round");
		}
		position = 0;
		this.nextAction = MoverAction.ENTER(round.getOrigin());
		
	}
	
	@Override
	public void setUpListeners() {
		// After the agents leave the parking, it moves until it finished the round
		((DriverBody) getAgentBody()).addOnParkingLeftListener((event) ->
			nextAction = MoverAction.MOVE()
			);
		
		// When the agent enter the parking, it waits
		((DriverBody) getAgentBody()).addOnParkingEnteredListener((event) ->
			nextAction = MoverAction.WAIT()
			);

		//goes to work between 7h and 8h59
		Date departure = new Date(0, WeekDay.MONDAY, random.nextInt(2) + 7, random.nextInt(60));
		departures[0] = departure;
		
		SmartGov
		.getRuntime()
		.getClock()
		.addDelayedAction(
			new DelayedActionHandler(
					departure,
					() -> {
						/*Run.logger.info("[" + SmartGov.getRuntime().getClock().getHour()
								+ ":" + SmartGov.getRuntime().getClock().getMinutes() + "]"
								+ "Agent " + getAgentBody().getAgent().getId()
								+ " leaves his home "
								);*/

						nextAction = MoverAction.LEAVE(round.getOrigin());
						triggerRoundDepartureListeners(new RoundDeparture());
					}
					)
			);
		
		//leaves work between 16h and 18h59
		departure = new Date(0, WeekDay.MONDAY, random.nextInt(3) + 16, random.nextInt(60));
		departures[1] = departure;
		SmartGov
		.getRuntime()
		.getClock()
		.addDelayedAction(
			new DelayedActionHandler(
					departure,
					() -> {
						/*Run.logger.info("[" + SmartGov.getRuntime().getClock().getHour()
								+ ":" + SmartGov.getRuntime().getClock().getMinutes() + "]"
								+ "Agent " + getAgentBody().getAgent().getId()
								+ " left work "
								);*/
						nextAction = MoverAction.LEAVE(round.getEstablishments().get(0));
						triggerRoundDepartureListeners(new RoundDeparture());
					}
					)
			);
		
		((DriverBody) getAgentBody()).addOnDestinationReachedListener((event) -> {
			if ( position == 0 ) {
				//he arrives at work
				/*Run.logger.info("[" + SmartGov.getRuntime().getClock().getHour()
						+ ":" + SmartGov.getRuntime().getClock().getMinutes() + "]"
						+ "Agent " + getAgentBody().getAgent().getId()
						+ " arrived at work "
						);*/
				refresh(round.getEstablishments().get(0).getClosestOsmNode(),
						round.getOrigin().getClosestOsmNode());
				nextAction = MoverAction.ENTER(round.getEstablishments().get(0));
				position += 1;
				journeyTime += ExtendedDate.getTimeBetween(departures[0], SmartGov.getRuntime().getClock().time());
			} else {
				//he is back home
				/*Run.logger.info("[" + SmartGov.getRuntime().getClock().getHour()
						+ ":" + SmartGov.getRuntime().getClock().getMinutes() + "]"
						+ "Agent " + getAgentBody().getAgent().getId()
						+ " is back home "
						);*/
				nextAction = MoverAction.ENTER(round.getOrigin());
				journeyTime += ExtendedDate.getTimeBetween(departures[1], SmartGov.getRuntime().getClock().time());
				personality.giveTime(journeyTime);
				personality.computeSatisfactionOfAgent();
				personality.giveSatisfactionToNeighborhoods();
				triggerRoundEndListeners(new RoundEnd());
			}
		});
		
	}
}
