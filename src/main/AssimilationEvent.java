package main;

public abstract class AssimilationEvent {

    public float delay;
    public float timeFrame;
    public Assimilation game;

    public AssimilationEvent(float delay, float timeFrame, Assimilation game){
        this.delay = delay;
        this.timeFrame = timeFrame;
        this.game = game;
    }

    abstract public void execute();

}
