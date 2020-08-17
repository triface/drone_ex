package triface.drone;

public class DistressException extends Exception
{
	public DistressException()
	{
		super();
	}
	
	public DistressException(String s)
	{
		super(s);
	}
	
	public DistressException(String s, Throwable t)
	{
		super(s, t);
	}
}
