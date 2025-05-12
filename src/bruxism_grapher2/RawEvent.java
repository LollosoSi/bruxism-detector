package bruxism_grapher2;

public class RawEvent {
	public long millis;
	public boolean value;
	public int fvalue;

	public RawEvent(long millis, boolean value, int fvalue) {
		this.millis = millis;
		this.value = value;
		this.fvalue = fvalue;
	}
}

