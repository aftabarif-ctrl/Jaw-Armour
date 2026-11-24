package com.example.titans;
/** Per-form stats. Tweak freely. */
public enum TitanType {
    //          scale health armor tough kbRes speed jump  damage
    NONE   (1.0,    0,   0,  0,  0.0, 0.0,  0.0,  0),
    ARMORED(5.0,  180,  20, 12,  1.0, 0.15, 0.25, 14),
    JAW    (3.5,   60,   6,  2,  0.3, 0.70, 0.50, 8);
    public final double scale, health, armor, toughness, kbRes, speed, jump, damage;
    TitanType(double s,double h,double a,double t,double k,double sp,double j,double d){scale=s;health=h;armor=a;toughness=t;kbRes=k;speed=sp;jump=j;damage=d;}
    public static TitanType of(int i){ var v=values(); return i>=0&&i<v.length?v[i]:NONE; }
}
