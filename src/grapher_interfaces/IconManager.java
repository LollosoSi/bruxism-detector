package grapher_interfaces;

public interface IconManager<Color, Image> {
	public Image loadImage(String imagepath, Color recolor);
	public Image loadImage(String imagepath, String recolor);
}
