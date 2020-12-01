package main;

import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;

public class AssimilationData {

    public static BulletType getLLaser(){
        return new BasicBulletType(10f, 140*2, "bullet"){{
            width = 12f;
            height = 12f;
            lifetime = 20f;
        }};

    }
}
