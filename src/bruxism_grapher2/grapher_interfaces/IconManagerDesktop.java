package bruxism_grapher2.grapher_interfaces;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

import javax.imageio.ImageIO;

public class IconManagerDesktop implements IconManager<Color, BufferedImage> {

	public IconManagerDesktop() {
		
	}

	public BufferedImage recolorPng(BufferedImage originalImage, Color tintColor) {
        // Controllo di sicurezza: se l'immagine è null, non tentiamo di ricolorarla
        if (originalImage == null) return null;

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
            BufferedImage img = null;
            
            // 1. Tenta la lettura come file fisico su disco (Rimuove il primo slash per i percorsi relativi)
            String diskPath = imagepath.startsWith("/") ? imagepath.substring(1) : imagepath;
            File iconFile = new File(diskPath);
            
            if (iconFile.exists()) {
                img = ImageIO.read(iconFile);
            } else {
                // 2. Se non lo trova sul disco, tenta di cercarlo nel Classpath (utile se esporti il progetto in JAR)
                URL resource = getClass().getResource(imagepath);
                if (resource != null) {
                    img = ImageIO.read(resource);
                }
            }
            
            // Se abbiamo trovato l'immagine, la ricoloriamo
            if (img != null) {
                return recolorPng(img, recolor);
            } else {
                // Invece di crashare, avvisiamo la console che manca un'icona e andiamo avanti
                System.err.println("Attenzione: Icona non trovata -> " + imagepath);
                return null;
            }

		} catch (Exception e) {
			System.err.println("Errore durante la lettura dell'icona: " + imagepath);
			return null;
		}
	}

	@Override
	public BufferedImage loadImage(String imagepath, String recolor) {
		// Aggiunge /src/ prima del nome del file come da tua indicazione
		return loadImage("/" + imagepath, Color.decode(recolor));
	}

}