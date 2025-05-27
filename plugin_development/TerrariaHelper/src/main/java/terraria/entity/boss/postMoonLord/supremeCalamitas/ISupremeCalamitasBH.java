package terraria.entity.boss.postMoonLord.supremeCalamitas;

public interface ISupremeCalamitasBH {
    boolean isStrict();
    double healthRatio();
    boolean inProgress();
    void begin();
    void tick();
    void finish();
    void refresh();
}
