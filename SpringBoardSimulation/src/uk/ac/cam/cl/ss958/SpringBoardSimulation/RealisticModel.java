package uk.ac.cam.cl.ss958.SpringBoardSimulation;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.sun.org.apache.xerces.internal.impl.xpath.XPath.Step;

import uk.ac.cam.cl.ss958.IntegerGeometry.Point;

public class RealisticModel extends SimulationModel {
	static Random generator =  new Random (System.currentTimeMillis());

	private static final int SIMULATION_WAITING_INVLAMBDA = 400;
	private static final int SIMULATION_STEP_LENGH_MS = 1;

	private static final int SIMULATION_DAY = 10000;
	private static final int SIMULATION_MORNING = 5000;
	private static final int SIMULATION_EVENING = 5000;
	private static final int SIMULATION_COMMUNITIES = 50;
	private static final int SIMULATION_START_USERS = 400;

	private final static boolean DEBUG_LATEST_USER = false;
	Map<Integer, RealisticUser> users;

	private int squareWidth;
	private int squareHeight;

	public int getSquareWidth() { return squareWidth; }
	public int getSquareHeight() { return squareHeight; }

	private long simulationSteps = 0;

	public long getStepsExecuted() {
		return simulationSteps;
	}

	public int getStepLengthMs() {
		return SIMULATION_STEP_LENGH_MS;
	}

	Community [] communities;

	private static class SimulationEvent implements Comparable<SimulationEvent>{
		public long time;
		public RealisticUser node;
		public SimulationEvent(long time, RealisticUser node) {
			this.time = time;
			this.node = node;
		}
		@Override
		public int compareTo(SimulationEvent o) {
			if (this.time == o.time) {
				return ((Integer)node.getID()).compareTo(o.node.getID());
			} else {
				return ((Long)time).compareTo(o.time);
			}
		}
	}

	PriorityQueue<SimulationEvent> simulationEvents;
	Set<RealisticUser> activeUsers;

	public RealisticModel(int width, int height, int squareWidth, int squareHeight) throws Exception {
		super(width, height);
		assert SIMULATION_MORNING + SIMULATION_EVENING == SIMULATION_DAY;

		users = new HashMap<Integer, RealisticUser>();

		assert width % squareWidth == 0 && height % squareHeight == 0;

		this.squareWidth = squareWidth;
		this.squareHeight = squareHeight;

		communities = new Community[SIMULATION_COMMUNITIES];

		for (int i=0; i<SIMULATION_COMMUNITIES; ++i) {
			communities[i] = new Community(this);
		}

		activeUsers = new TreeSet<RealisticUser>();
		simulationEvents = new PriorityQueue<SimulationEvent>();

		for(int i=0; i < SIMULATION_START_USERS; ++i) {
			AddRandomUser();
		}

		System.out.println("Total number of users placed: " + User.getNumerOfUsers());

		running = new AtomicBoolean(false);

	}

	private JLabel timeLabel;

	private JLabel executionTime;

	private JCheckBox drawSocialGraph;
	@Override
	public void addToOptionsMenu(final GlobalOptionsPanel o) {
		super.addToOptionsMenu(o);
		timeLabel = new JLabel("");
		o.addElement(timeLabel, 30);

		executionTime = new JLabel("");
		o.addElement(executionTime, 30);
		updateTimeLabel();

		drawSocialGraph = new JCheckBox();
		drawSocialGraph.setSelected(true);
		drawSocialGraph.setText("Social Graph");
		drawSocialGraph.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onChange();
			}
		});
		o.addElement(drawSocialGraph, 30);
		
		JButton socialStats = new JButton("Social Graph Details");
		
		o.addElement(socialStats, 30);
		
		socialStats.addActionListener(new ActionListener() {		
			@Override
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showMessageDialog(o, computeSocialGraphStats());
			}
		});
	}

	public void updateTimeLabel() {
		if (timeLabel == null) return;
		long stepsInDay = getStepsExecuted()%SIMULATION_DAY;
		boolean isMorning = stepsInDay < SIMULATION_MORNING;
		timeLabel.setText("Time: " + stepsInDay +"/" + SIMULATION_DAY + " " +
				(isMorning ? "(morning)" : "(evening)"));

		executionTime.setText("ms per step: " + String.format("%.3G", new Double(averageStepExecutionTime)));
	}

	AtomicBoolean running;

	double averageStepExecutionTime = -1;

	public void simulationStep() {
		updateTimeLabel();

		if(running.compareAndSet(false, true)) {
			long startTime = System.nanoTime();
			++simulationSteps;

			while(simulationEvents.peek() != null &&
					simulationEvents.peek().time <= simulationSteps) {
				activeUsers.add(simulationEvents.poll().node);
			}
			List<RealisticUser> usersToRemove = new ArrayList<RealisticUser>();
			for (RealisticUser u: activeUsers) {
				long nextWakeUp = u.maybeMove();
				if (nextWakeUp > 0) {
					simulationEvents.add(new SimulationEvent(nextWakeUp, u));
					usersToRemove.add(u);
				}
			}

			for (RealisticUser u : usersToRemove) {
				activeUsers.remove(u);
			}

			onChange();
			running.set(false);

			long executionTime = System.nanoTime() - startTime;
			double executionTimeMS = (double)executionTime/1000000.0;
			if (averageStepExecutionTime == -1) {
				averageStepExecutionTime = executionTimeMS;
			} else {
				averageStepExecutionTime = 0.999*averageStepExecutionTime + 0.001 * executionTimeMS;
			}
		}
	}

	public boolean AddRandomUser() {
		try {
			int communityIndex = generator.nextInt(communities.length);
			RealisticUser u = new RealisticUser(this, communities[communityIndex]);
			users.put(u.getID(), u);
			activeUsers.add(u);
			onChange();
			return true;
		} catch(CannotPlaceUserException e) {
			onChange();
			return false;
		}
	}

	public void clearUsers() {
		users.clear();
		selectedUser = -1;
		onChange();
	}

	@Override
	public Map<Integer, ? extends User> getUsers() {
		return users;
	}

	@Override
	public void prepaint(Graphics g) {
		g.setColor(new Color(220, 220, 220));
		int numW = width/squareWidth;
		int numH = height/squareHeight;
		for(int i=1; i<numW; ++i) {
			g.drawLine(i*squareWidth, 0, i*squareWidth, height-1);
		}
		for(int j=1; j<numH; ++j) {
			g.drawLine(0, j*squareHeight, width-1, j*squareHeight);
		}
	}

	@Override
	public void postpaint(Graphics g) {
		if (drawSocialGraph.isSelected()) {
			g.setColor(new Color(200,0,0));
			for (Integer i : users.keySet()) {
				SocialUser u = users.get(i);
				for (User friend : u.getFriends()) {
					if (u.getID() < friend.getID()) {
						g.drawLine(u.getLocation().getX(), u.getLocation().getY(),
								friend.getLocation().getX(), friend.getLocation().getY());
					}
				}
			}
			super.postpaint(g);
		}
	}



	private static class Community {
		// top left corner of that square
		private Point morningHome, morningRemote;
		private Point eveningHome, eveningRemote;

		private int pHome, pRoam; // probability multiplied by 1000.

		private RealisticModel m;

		public Community(RealisticModel model) {
			m = model;

			int numW = m.getWidth()/m.getSquareWidth();
			int numH = m.getHeight()/m.getSquareHeight();

			morningHome = 
					new Point(generator.nextInt(numW)*m.getSquareWidth(),
							generator.nextInt(numH)*m.getSquareHeight());

			morningRemote =
					new Point(generator.nextInt(numW)*m.getSquareWidth(),
							generator.nextInt(numH)*m.getSquareHeight());

			eveningHome = 
					new Point(generator.nextInt(numW)*m.getSquareWidth(),
							generator.nextInt(numH)*m.getSquareHeight());

			eveningRemote =
					new Point(generator.nextInt(numW)*m.getSquareWidth(),
							generator.nextInt(numH)*m.getSquareHeight());

			pRoam = generator.nextInt(100)+1; // between 0.001 and 0.1
			pHome = 1000-pRoam;

			Point [] arr = new Point[] {morningHome, morningRemote,
					eveningHome, eveningRemote };
			/*
			System.out.println("Generating community");
			for (int i=0; i<arr.length; ++i) {
				System.out.println("" + arr[i].getX()+","+arr[i].getY());
			}
			System.out.println();
			 */
		}

		public Point getSquareForNextTarget() {
			long stepsInDay = m.getStepsExecuted()%SIMULATION_DAY;
			boolean isMorning = stepsInDay < SIMULATION_MORNING;

			Point offset = new Point(generator.nextInt(m.getSquareWidth()),
					generator.nextInt(m.getSquareHeight()));

			Point ret;

			if (generator.nextInt(1000)>=pHome) { // roam
				if (isMorning) ret = morningRemote;
				else ret = eveningRemote;
			} else { // home
				if (isMorning) ret = morningHome;
				else ret = eveningHome;
			}

			ret = ret.add(offset);

			return new Point(
					Math.min(
							Math.max(ret.getX(), User.USER_RADIUS+1),
							m.getWidth()-User.USER_RADIUS-1
							),
							Math.min(
									Math.max(ret.getY(), User.USER_RADIUS+1),
									m.getHeight()-User.USER_RADIUS-1)
					);
		}
	}

	public String computeSocialGraphStats() {
		List<List<Integer>> adjacency =
				new ArrayList<List<Integer>>(User.getNumerOfUsers());

		for (int i = 0; i<User.getNumerOfUsers(); ++i) {
			adjacency.add(new ArrayList<Integer>());
		}
		
		for (Integer i : users.keySet()) {
			SocialUser u = users.get(i);
			List<Integer> friends = adjacency.get(u.getID());
			for (User friend : u.getFriends()) {
				friends.add(friend.getID());
			}
		}
		GraphProperties.Graph graph =
				new GraphProperties.Graph(User.getNumerOfUsers(), adjacency);
		
		return (new GraphProperties(graph)).toString();
	}




	private static class RealisticUser extends SocialUser {

		private long wakeMeUpAt = 0;
		private RealisticMoveGenerator moveGenerator;

		private Community community;

		private RealisticModel model;

		RealisticUser(final RealisticModel m, Community c) throws CannotPlaceUserException {
			super(m);

			model = m;

			community = c;

			moveGenerator = new RealisticMoveGenerator(possibleSteps) {
				protected boolean validate(int move) {
					return model.validatePosition(getLocation().add(possibleSteps[move]), id);
				}

				@SuppressWarnings("unused")
				protected Point getCurrentLocation() {
					return getLocation();
				}

				@SuppressWarnings("unused")
				protected Point getNewTarget() {
					return community.getSquareForNextTarget();
				}

				protected RealisticModel getModel() {
					return model;
				}
			};
		}

		private JLabel locationLabel;
		private JLabel targetLabel;

		@Override
		public void setLocation(Point location) throws CannotPlaceUserException {
			super.setLocation(location);
			maybeSetLabels();
		}

		private void maybeSetLabels() {
			if (optionsPanel == null || 
					locationLabel == null ||
					targetLabel == null) return;

			Point t = moveGenerator.getTarget();
			if(wakeMeUpAt <= model.getStepsExecuted() && t != null) {
				targetLabel.setText(""+t.getX()+","+t.getY());
			} else {
				targetLabel.setText("waiting");
			}
			locationLabel.setText("" + getLocation().getX() + "," + getLocation().getY());

		}

		@Override
		public void setOptionsPanel(UserOptionsPanel panel) {
			super.setOptionsPanel(panel);
			if (panel != null) {
				JPanel additionalPanel = new JPanel(new GridBagLayout());
				additionalPanel.setBorder(new EmptyBorder(0, 0, 0, 0) );

				GridBagConstraints c = new GridBagConstraints();
				c.fill = GridBagConstraints.HORIZONTAL;
				c.insets = new Insets(2,2,2,2);
				c.anchor = GridBagConstraints.NORTH;

				c.gridx = 0; c.gridy=0; c.weighty = 0.0;
				additionalPanel.add(new JLabel("Location: "),c);
				c.gridx = 1; c.gridy=0; c.weighty = 0.0;
				locationLabel = new JLabel("");
				additionalPanel.add(locationLabel,c);

				c.gridx = 0; c.gridy=1; c.weighty = 0.0;
				additionalPanel.add(new JLabel("Target: "),c);
				c.gridx = 1; c.gridy=1; c.weighty = 0.0;
				targetLabel = new JLabel("");
				additionalPanel.add(targetLabel,c);

				maybeSetLabels();

				c.gridx = 0; c.gridy = 2; c.weighty = 0.0;
				panel.add(additionalPanel,c);
			}
		}

		private static final Point [] possibleSteps = { 
			new Point(-1,0),
			new Point(0,-1),
			new Point(1,0),
			new Point(0,1)
		};

		// returns 0 if moving otherwise returns time of next wakeUp
		public long maybeMove() {
			if(wakeMeUpAt <= model.getStepsExecuted()) {
				try {
					int step = moveGenerator.advance();
					if (step == -1) throw new CannotPlaceUserException();
					setLocation(getLocation().add(possibleSteps[step]));
				} catch (CannotPlaceUserException e) { }
				if (moveGenerator.movingDone()) {
					wakeMeUpAt = model.getStepsExecuted() +
							(int)generator.nextExponential(
									1.0/SIMULATION_WAITING_INVLAMBDA);
					maybeSetLabels();
				}
				return 0;
			} else {
				return wakeMeUpAt;
			}
		}
	}

	private static abstract class RealisticMoveGenerator {

		private static Point [] possibleSteps;

		public RealisticMoveGenerator(final Point [] possibleSteps) {
			this.possibleSteps = possibleSteps;
		}

		private Point target = null;

		public Point getTarget() {
			return target;
		}

		double square(int x) {
			return (double)x * x;
		}

		double getDistanceToTarget(Point from) {
			if (target == null) return 0;
			return square(from.getX()-target.getX()) +
					square(from.getY()-target.getY());

		}

		int stepsMade;
		double startingDistanceToTarget;

		public boolean embarassinglyBadProgress() {
			double distanceTraveled =
					startingDistanceToTarget -
					getDistanceToTarget(getCurrentLocation());
			return stepsMade > 15 && distanceTraveled/square(stepsMade) < 0.5;
		}

		int backOffTime = 0;
		int backOffStep = 0;

		public int advance() {

			if (movingDone())  {
				target = getNewTarget();
				stepsMade = 0;
				startingDistanceToTarget = getDistanceToTarget(getCurrentLocation());
			}

			if(embarassinglyBadProgress()) {
				stepsMade = 0;
				backOffTime = generator.nextInt(100);
				backOffStep = generator.nextInt(possibleSteps.length);
			}

			if(backOffTime > 0) {
				backOffTime--;
				if (validate(backOffStep)) {
					return backOffStep;
				} else {
					return -1;
				}
			}

			double minDistance = 1e100;
			int bestMove = -1;
			for (int i=0; i< possibleSteps.length; ++i) {
				if (validate(i)) {
					double curDistance = getDistanceToTarget(getCurrentLocation().add(possibleSteps[i]));
					if(curDistance < minDistance) {
						minDistance = curDistance;
						bestMove = i;
					}
				}
			}
			++stepsMade;
			return bestMove;
		}

		public boolean movingDone() {
			return target == null || Tools.pointsEqual(target, getCurrentLocation());
		}

		protected boolean validate(int move) {
			return false;
		}

		protected Point getCurrentLocation() { return null; }

		protected Point getNewTarget() { return null; }

		protected abstract RealisticModel getModel();
	}


}
