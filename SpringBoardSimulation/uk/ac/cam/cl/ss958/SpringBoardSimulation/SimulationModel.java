package uk.ac.cam.cl.ss958.SpringBoardSimulation;

import uk.ac.cam.cl.ss958.IntegerGeometry.*;

import java.util.ArrayList;
import java.util.Random;

public class SimulationModel {	
	class UserInModel {
		final public User user;
		public Point location;
		
		UserInModel(User user, Point location) {
			this.user = user;
			this.location = location;
		}
	}
	
	enum SoftError {
		NONE,
		WRONG_MOVING;
	}

	public static final int USER_RADIUS = 10;

	final Random generator = new Random (System.currentTimeMillis());
	
	private int width;
	private int height;

	private int selectedUser;
	private Point selectedUserClickTranslation;
	private ArrayList<UserInModel> users;
	
	private SoftError softError = SoftError.NONE;
	
	public boolean isSoftError() {
		return softError != SoftError.NONE;
	}
	
	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}
	
	SimulationModel(int width, int height) {
		this.width = width;
		this.height = height;
		users = new ArrayList<UserInModel>();
		selectedUser = -1;
		onChange();
	}
	
	public ArrayList<UserInModel> getUsers() {
		return users;
	}
	
	boolean ValidatePosition(Point x, int excludingIndex) {
		if (x.getX() <= USER_RADIUS || 
		    x.getX() >= width-USER_RADIUS ||
		    x.getY() <= USER_RADIUS ||
		    x.getY() >= height - USER_RADIUS) return false;
		for (int i = 0; i < users.size(); ++i) {
			if(i != excludingIndex && Compute.euclideanDistanceSquared(x, users.get(i).location) <= Compute.square(2*USER_RADIUS)) {
				return false;
			}
		}
		return true;
	}
	
	public boolean AddUserAtRandomLocation(User user) {
		int retries = 5;
		while(retries-- >= 0) {
			Point location = new Point(generator.nextInt(width), generator.nextInt(height));
			if(ValidatePosition(location, -1)) {
				users.add(new UserInModel(user, location));
				onChange();
				return true;
			}
		}
		return false;
	}
	
	public int getSelectedUser() {
		return selectedUser;
	}

	public void maybeSelectUser(Point p) {
		for(int i=0; i<users.size(); ++i) {
			if(Compute.euclideanDistanceSquared(p, users.get(i).location) <= Compute.square(USER_RADIUS)) {
				selectedUserClickTranslation = users.get(i).location.sub(p);
				selectedUser = i;
				onChange();
				return;
			}
		}
		selectedUser = -1;
		onChange();
	}
	
	public void maybeMoveUser(Point p) {
		if (selectedUser != -1) {
			Point newLocation = p.add(selectedUserClickTranslation);
			if(ValidatePosition(newLocation, selectedUser)) {
				users.get(selectedUser).location = newLocation;
				removeErrorIfPresent(SoftError.WRONG_MOVING);
			} else {
				softError = SoftError.WRONG_MOVING;
			}
			onChange();
		}
	}
	
	public void movingFinished() {
		removeErrorIfPresent(SoftError.WRONG_MOVING);
	}
	public void clearUsers() {
		users.clear();
		selectedUser = -1;
		onChange();
	}
	
	
	protected void onChange() {
	}
	
	private void removeErrorIfPresent(SoftError se) {
		if (softError == se) {
			softError = SoftError.NONE;
			onChange();
		}
	}

}