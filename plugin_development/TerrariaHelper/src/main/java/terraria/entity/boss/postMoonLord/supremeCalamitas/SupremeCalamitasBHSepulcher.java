package terraria.entity.boss.postMoonLord.supremeCalamitas;

public class SupremeCalamitasBHSepulcher implements ISupremeCalamitasBH {
    SupremeCalamitas owner;

    SupremeCalamitasBHSepulcher(SupremeCalamitas owner) {
        this.owner = owner;
    }

    @Override
    public boolean isStrict() {
        return false;
    }

    @Override
    public double healthRatio() {
        return 1.1;
    }

    @Override
    public boolean inProgress() {
        return false;
    }

    @Override
    public void refresh() {
    }

    @Override
    public void begin() {
        new Sepulcher(owner);
    }

    @Override
    public void finish() {
    }

    @Override
    public void tick() {
    }
}
