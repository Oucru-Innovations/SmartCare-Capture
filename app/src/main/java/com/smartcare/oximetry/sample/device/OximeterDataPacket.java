package com.smartcare.oximetry.sample.device;

public class OximeterDataPacket {
    public int pleth;
    public int pulse;
    public double spo2;
    public int deviceId;
    public double battery;
    public int counter;
    public int spo2Status;
    public long redAdc;
    public long irAdc;
    public double perfusionIndex;
    public long packageReceived;
    public int startTime;

    public OximeterDataPacket(int pleth, int pulse, double spo2, int deviceId, double battery,
                              int counter, int spo2Status, long redAdc, long irAdc,
                              double perfusionIndex, long packageReceived, int startTime) {
        this.pleth = pleth;
        this.pulse = pulse;
        this.spo2 = spo2;
        this.deviceId = deviceId;
        this.battery = battery;
        this.counter = counter;
        this.spo2Status = spo2Status;
        this.redAdc = redAdc;
        this.irAdc = irAdc;
        this.perfusionIndex = perfusionIndex;
        this.packageReceived = packageReceived;
        this.startTime = startTime;
    }
}
