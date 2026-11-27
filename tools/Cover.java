import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
/** Original typographic cover; build-time tool only. */
class Cover {
    public static void main(String[] args)throws Exception{
        var image=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14,19,26));g.fillRect(0,0,512,512);
        g.setColor(new Color(77,219,166));g.fillRect(44,79,92,8);
        g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,57));g.drawString("VMCompat",44,197);
        g.setFont(new Font("SansSerif",Font.PLAIN,24));g.drawString("macOS / OpenGL",44,258);
        g.setColor(new Color(255,176,68));g.setFont(new Font("SansSerif",Font.BOLD,20));g.drawString("PRIVATE PREVIEW",44,432);
        g.dispose();ImageIO.write(image,"png",new File(args[0]));
    }
}
