package grapher_interfaces;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class GrapherDesktop extends GrapherInterface<Color, BufferedImage, Font> {
	
	Graphics2D g;
	BufferedImage finalimage;
	
	public GrapherDesktop(int width, int height) {
		this.setImageSize(width, height);
		
		finalimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		g = finalimage.createGraphics();
	}

	@Override
	public void setColor(Color c) {
		super.setColor(c);
		g.setColor(c);
	}
	
	@Override
	public void drawLine(int x1, int y1, int x2, int y2) {
		g.drawLine(x1, y1, x2, y2);
	}

	@Override
	public void drawString(String str, int x, int y) {
		g.drawString(str, x, y);
	}

	@Override
	public void drawRect(int x, int y, int width, int height) {
		g.drawRect(x, y, width, height);
	}

	@Override
	public void fillRect(int x, int y, int width, int height) {
		g.fillRect(x, y, width, height);
	}

	@Override
	public void drawImage(BufferedImage img, int x, int y, int width, int height) {
		g.drawImage(img, x, y, width, height, null);
	}

	@Override
	public boolean writeImage(BufferedImage img, String file_name) {
		try {
			ImageIO.write(img, "png", new File(file_name.replace(".csv", ".png")));
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public BufferedImage getImage() {
		return finalimage;
	}

	@Override
	public void setFont(String fontname, int size) {
		g.setFont(new Font(fontname, Font.BOLD, size));
	}

	@Override
	public Color convertColor(String colorstring) {
		return Color.decode(colorstring);
	}




}
