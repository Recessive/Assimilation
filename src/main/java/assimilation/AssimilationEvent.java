package assimilation;

public abstract class AssimilationEvent {

    public float delay;
    public float timeFrame;

    public AssimilationEvent(float delay, float timeFrame){
        this.delay = delay;
        this.timeFrame = timeFrame;
    }

    abstract public void execute();

}
