package grapher_interfaces;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

public class IconManagerDesktop implements IconManager<Color, BufferedImage> {

	public IconManagerDesktop() {
		
	}

	public BufferedImage recolorPng(BufferedImage originalImage, Color tintColor) {
		BufferedImage tintedImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(),
				BufferedImage.TYPE_INT_ARGB);

		Graphics2D g2d = tintedImage.createGraphics();

		// Draw the original image's alpha (transparency)
		g2d.drawImage(originalImage, 0, 0, null);

		// Apply the tint color using SRC_ATOP to color only non-transparent pixels
		g2d.setComposite(AlphaComposite.SrcAtop);
		g2d.setColor(tintColor);
		g2d.fillRect(0, 0, originalImage.getWidth(), originalImage.getHeight());

		g2d.dispose();
		return tintedImage;
	}
	
	@Override
	public BufferedImage loadImage(String imagepath, Color recolor) {
		try {
			return recolorPng(ImageIO.read(getClass().getResource(imagepath)), recolor);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public BufferedImage loadImage(String imagepath, String recolor) {
		// TODO Auto-generated method stub
		return loadImage("/"+imagepath, Color.decode(recolor));
	}

}
