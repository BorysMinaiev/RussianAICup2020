package model;

import util.StreamUtil;

public class Color {
    private float r;

    public float getR() {
        return r;
    }

    public void setR(float r) {
        this.r = r;
    }

    private float g;

    public float getG() {
        return g;
    }

    public void setG(float g) {
        this.g = g;
    }

    private float b;

    public float getB() {
        return b;
    }

    public void setB(float b) {
        this.b = b;
    }

    private float a;

    public float getA() {
        return a;
    }

    public void setA(float a) {
        this.a = a;
    }

    public Color() {
    }

    public Color(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public static Color RED = new Color(1.0f, 0.0f, 0.0f, 1.0f);
    public static Color TRANSPARENT_RED = new Color(1.0f, 0.0f, 0.0f, 0.3f);
    public static Color BLUE = new Color(0.0f, 0.0f, 1.0f, 1.0f);
    public static Color TRANSPARENT_BLUE = new Color(0.0f, 0.0f, 1.0f, 0.3f);
    public static Color GREEN = new Color(0.0f, 1.0f, 0.0f, 1.0f);
    public static Color ORANGE = new Color(1.0f, 165.0f, 0.0f, 1.0f);
    public static Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static Color YELLOW = new Color(1.0f, 1.0f, 0.0f, 1.0f);

    public static Color readFrom(java.io.InputStream stream) throws java.io.IOException {
        Color result = new Color();
        result.r = StreamUtil.readFloat(stream);
        result.g = StreamUtil.readFloat(stream);
        result.b = StreamUtil.readFloat(stream);
        result.a = StreamUtil.readFloat(stream);
        return result;
    }

    public void writeTo(java.io.OutputStream stream) throws java.io.IOException {
        StreamUtil.writeFloat(stream, r);
        StreamUtil.writeFloat(stream, g);
        StreamUtil.writeFloat(stream, b);
        StreamUtil.writeFloat(stream, a);
    }
}
