package com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces;

public abstract class GrapherInterface<Color, Image, Font> {

	int width, height;
	Color currentColor;

	public void setImageSize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	// Draw a line
	public abstract void drawLine(int x1, int y1, int x2, int y2);

	// Draw some text
	public abstract void drawString(String str, int x, int y);

	// Draw a rect
	public abstract void drawRect(int x, int y, int width, int height);

	// Fill some rect with color
	public abstract void fillRect(int x, int y, int width, int height);

	// Draw some generic image
	public abstract void drawImage(Image img, int x, int y, int width, int height);

	// Sets a generic color
	public void setColor(Color c) {currentColor=c;}

	public abstract boolean writeImage(Image img, String file_name);
	public abstract Image getImage();
	public abstract void setFont(String fontname, int size);

	public abstract Color convertColor(String colorstring);
}
