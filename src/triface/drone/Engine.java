package triface.drone;

import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Engine will have a power of TAKEOFF_POWER after takeoff and hovering.
 * For roll, pitch and thrust, the engine will use POWER_STEP
 * During takeoff and landing, the engine will take bigger jumps NUM_STEPS
 * 
 * @author sudhir
 *
 */

public class Engine extends Observable
{
	public enum ENGINE_STATUS {OFF, READY, ON};
	
	private final static int MAX_POWER = 100;
	public final static int HOVERING_POWER = 20;
	public final static int TAKEOFF_POWER = 30;
	public final static int POWER_STEP = 5;
	
	public final static int NUM_STEPS = 6;		//used for takeoff and landing
	
	private final int id;
	private int power;
	private boolean isLeft;
	private boolean isClockwise;
	private ENGINE_STATUS status;
	private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
	private Timer monitorTimer;


	public Engine(int id, boolean isClockwise, Observer observer)
	{
		this.id = id;
		this.isClockwise = isClockwise;
		if (id % 2 == 0)
			isLeft = true;
		status = ENGINE_STATUS.READY;
		this.addObserver(observer);
		monitorTimer = new Timer();
		monitorTimer.scheduleAtFixedRate(monitorTask, 1000, 1000);	
	}
	
	public void shutdown()
	{
		status = ENGINE_STATUS.OFF;
	}
	
	public boolean changePower(int dp)
	{
		return changePower(dp, 1);
	}

	public boolean changePower(int dp, int numSteps)
	{
		Lock writeLock = rwLock.writeLock();
		writeLock.lock();
		int stepSize = POWER_STEP;
		if (numSteps > 0)
			stepSize *= numSteps;
				
		try {
			int newPower = this.power + dp;
			
			//Do nothing
			if (newPower < 0 || newPower > MAX_POWER)
				return false;
			
			//Change the power in incremental steps
			if (newPower > power)
				incrementPower(newPower, stepSize);
			else
				decrementPower(newPower, stepSize);
		}
		finally 
		{
			writeLock.unlock();
		}

		return true;
	}

	public int getPower() {
        Lock readLock = rwLock.readLock();
        readLock.lock();
        try {
        	return power;
        }
        finally 
        {
            readLock.unlock();
        }
    }


	public boolean isLeft() {
		return isLeft;
	}

	public boolean isClockwise() {
		return isClockwise;
	}

	public int getEngineId() {
		return id;
	}
	
	public String getStatus() {
		return status.toString();
	}

	public void setStatus(ENGINE_STATUS newStatus) {
		this.status = newStatus;
	}
	
	private void incrementPower(int newPower, int stepSize)
	{
		System.out.println("\tIncrementing power, engine " + id + ": " + newPower);
		while (power < newPower)
		{
			power += Math.min(stepSize, newPower - power);
			if (power > MAX_POWER)
			{
				power = MAX_POWER;
				break;
			}
		}
	}
	
	private void decrementPower(int newPower, int stepSize)
	{
		System.out.println("\tDecrementing power, engine " + id + ": " + newPower);
		while (power > newPower)
		{
			power -= Math.min(stepSize, power-newPower);
			if (power < 0)
			{
				power = 0;
				break;
			}
		}
	}

	/**
	 * Task to monitor engine power. Sends distress signal when power is < 5%.
	 * If engine power < hovering power required, sends alert to adjust.
	 */
	private final TimerTask monitorTask = new TimerTask()
	{
		boolean notified = false;
		@Override
		public void run()
		{
			if (status == ENGINE_STATUS.ON)
			{
				if (power < 5)
				{
					System.err.println("Engine " + getEngineId() + " has low power: " + power
										+ ", sending distress signal");
					status = ENGINE_STATUS.OFF;
					setChanged();
					notifyObservers();
				}
				else if (power < HOVERING_POWER && !notified)
				{
					System.err.println("ALERT: Engine " + getEngineId() + " power: " + power
							+ " < Hovering power (" + HOVERING_POWER + "). Please adjust");
					notified = true;
				}
			}
		}
	};
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(getEngineId());
		sb.append(", Power:");
		sb.append(getPower());
//		sb.append("\t");
//		sb.append(isClockwise()? "Clock" : "AntiClock");
		return sb.toString();
	}

}
