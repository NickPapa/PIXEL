
package org.onebeartoe.pixel.enterpirse.edition;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.RgbLedMatrix;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.pc.IOIOPcApplicationHelper;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.Timer;

import org.onebeartoe.pixel.hardware.Pixel;

/**
 * @author rmarquez
 */
@WebServlet(value = "/status", loadOnStartup=1)
public class InitializationServlet extends HttpServlet implements IOIOLooperProvider
{
// REMOVE THIS FROM CLASS SCOPE    
//    private static IOIO ioiO;
    
    private static RgbLedMatrix.Matrix MATRIX_TYPE = RgbLedMatrix.Matrix.SEEEDSTUDIO_32x32;
    
//    public static final Pixel pixel = new Pixel(MATRIX_TYPE);
    
    private Timer searchTimer;
    
    volatile protected Timer timer;
    
    private String statusLabel;
    
    private Logger logger;
    
    private HashMap<String, Font> fonts;
    
    public static final String [] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();

// MOVE THIS TO THE SCROLING TEXT CLASS    
    private int x;
    
    private final String PIXEL_KEY = "PIXEL_KEY";
    
    @Override
    public void init()
    {
        String className = InitializationServlet.class.getName();
        logger = Logger.getLogger(className);
     
        fonts = new HashMap();
        
        Pixel pixel = new Pixel(MATRIX_TYPE);
                
        // save the pixel to appliction scope
        ServletContext servletContext = getServletContext();     
        servletContext.setAttribute(PIXEL_KEY, pixel);
        
	startSearchTimer();
    
        x = 0;
        
        String [] args = {};
        try 
        {
            statusLabel = "Initializing";
            initializePixel(args);
        } 
        catch (Exception ex) 
        {
            statusLabel = "An error occureds while initializing";
            logger.log(Level.SEVERE, statusLabel, ex);
        }
    }
    
    @Override
    public void destroy()
    {
        searchTimer.stop();
                
        if(timer == null)
        {
            logger.log(Level.INFO, "The init servlet stopped with not timer set.");
        }
        else
        {
            timer.stop();
            logger.log(Level.INFO, "The init servlet stopped the REFRESH? timer.");
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
    {
        ServletContext servletContext = getServletContext();     
        Pixel pixel = (Pixel) servletContext.getAttribute(PIXEL_KEY);
        boolean initialized;
        if(pixel == null)
        {
            initialized = false;
        }
        else
        {
            initialized = true;
            
            String firmware = pixel.getFirmwareVersion();            
            request.setAttribute("firmware", firmware);
            
            String hardware = pixel.getHardwareVersion();
            request.setAttribute("hardware", hardware);
        }
        
        request.setAttribute("initialized", initialized);
        
        ServletContext c = getServletContext();
        RequestDispatcher rd = c.getRequestDispatcher("/status.jsp");
        rd.forward(request, response);
    }
    
    @Override
    public IOIOLooper createIOIOLooper(String connectionType, Object extra) 
    {
        System.out.println(getClass().getSimpleName() + " creating IOIO looper");
        
	return new BaseIOIOLooper() 
	{
	    private DigitalOutput led_;

	    @Override
	    protected void setup() throws ConnectionLostException, InterruptedException
	    {
		led_ = ioio_.openDigitalOutput(IOIO.LED_PIN, true);
                
//                InitializationServlet.this.ioiO = ioio_;
                
                
                ServletContext servletContext = getServletContext();     
                Pixel pixel = (Pixel) servletContext.getAttribute(PIXEL_KEY);
		pixel.matrix = ioio_.openRgbLedMatrix(pixel.KIND);
                pixel.ioiO = ioio_;

                StringBuilder message = new StringBuilder();
		message.append("Found PIXEL: " + pixel.matrix + "\n");
		message.append("You may now interact with the PIXEL\n");
		
//TODO: Load something on startup

		searchTimer.stop(); //need to stop the timer so we don't still display the pixel searching message
		message.append("PIXEL Status: Connected");
                statusLabel = message.toString();
                
                logger.log(Level.INFO, message.toString() );
	    }
	    
	    @Override
	    public void disconnected() 
	    {
		statusLabel = "PIXEL was disconected";
		System.out.println(statusLabel);
	    }

	    @Override
	    public void incompatible() 
	    {
		statusLabel = "Incompatible firmware detected";
		System.out.println(statusLabel);
	    }
	};
    }

    private final void initializePixel(String[] args) throws Exception 
    {
        System.out.println("initializing the IOIO application helper");
        
        IOIOPcApplicationHelper helper = new IOIOPcApplicationHelper(this);
//        helper.start();
        try
        {
            helper.start();
        }
        catch(UnsatisfiedLinkError e)
        {
            // we are not ont he Raspberry Pi or something went real bad
            e.printStackTrace();
        }
        
        try 
        {
            
            run(args);
        } 
        catch (Exception e) 
        {                
            throw e;
        } 
        finally 
        {                
            helper.stop();
        }
    }
    
    private void run(String[] argssss) throws Exception
    {
// DONT RUN THE SCROLLING TEXT HERE        
//        System.out.println("Starting PIXEL activity in " + getClass().getSimpleName() + ".");		
//	ActionListener listener = new TextScroller();
//	
//	// set the IOIO loop delay to half a second, by default
//	int delay = 500; // milliseconds
//        
//	timer = new Timer(delay, listener);	
//	timer.start();
    }
    
    private void startSearchTimer()
    {
	int delay = 1000;
	SearchTimer worker = new SearchTimer();
	searchTimer = new Timer(delay, worker);
	searchTimer.start();
    }
    
    private class SearchTimer implements ActionListener 
    {
	final long searchPeriodLength = 45 * 1000;
	
	final long periodStart;
	
	final long periodEnd;
	
	private int dotCount = 0;
	
	String message = "Searching for PIXEL";
	
	StringBuilder label = new StringBuilder(message);
	
	public SearchTimer()
	{
	    label.insert(0, "<html><body><h2>");
	    
	    Date d = new Date();
	    periodStart = d.getTime();
	    periodEnd = periodStart + searchPeriodLength;
	}
	
	public void actionPerformed(ActionEvent e) 
	{	    	    	    
	    if(dotCount > 10)
	    {
		label = new StringBuilder(message);
		label.insert(0, "<html><body><h2>");
		
		dotCount = 0;
	    }
	    else
	    {
		label.append('.');
	    }
	    dotCount++;

	    statusLabel = label.toString();
	    
	    Date d = new Date();
	    long now = d.getTime();
	    if(now > periodEnd)
	    {
		searchTimer.stop();
                
                ServletContext servletContext = getServletContext();     
                Pixel pixel = (Pixel) servletContext.getAttribute(PIXEL_KEY);
		if(pixel.matrix == null)
		{
		    message = "A Bluetooth connection to PIXEL could not be established. \n\nPlease ensure you have Bluetooth paired your PC to PIXEL first using code: 4545 and then try again.";		    		    
		    String title = "PIXEL Connection Unsuccessful";
                    statusLabel = title + message;
                    logger.log(Level.SEVERE, statusLabel);
		}
	    }
	}
    }

    public String getText()
    {
	return "some text";
    }
    
    /**
     * Override this to perform any additional background drawing on the image that get sent to the PIXEL
     * @param g2d 
     */
    protected void additionalBackgroundDrawing(Graphics2D g2d) throws Exception
    {
        
    }    
    
    /**
     * Override this to perform any additional foreground drawing on the image that get sent to the PIXEL
     * @param g2d 
     */
    protected void additionalForegroundDrawing(Graphics2D g2d) throws Exception
    {
        
    }
    
    private class TextScroller implements ActionListener
    {
        public void actionPerformed(ActionEvent e) 
        {
	    int delay = 200;//scrollSpeedSlider.getValue();	
	    delay = 710 - delay;                            // al linke: added this so the higher slider value means faster scrolling
	    
	    InitializationServlet.this.timer.setDelay(delay);
	    
            int w = 64;
            int h = 64;
	    
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            
	    Color textColor = Color.GREEN;//colorPanel.getBackground();
	    
            Graphics2D g2d = img.createGraphics();
            g2d.setPaint(textColor);
                      
            String fontFamily = fontNames[0];
//            String fontFamily = fontFamilyChooser.getSelectedItem().toString();
            
            Font font = fonts.get(fontFamily);
            if(font == null)
            {
                font = new Font(fontFamily, Font.PLAIN, 32);
                fonts.put(fontFamily, font);
            }            
            
            g2d.setFont(font);
            
            String message = getText();
            
            FontMetrics fm = g2d.getFontMetrics();
            
            int y = fm.getHeight();            

            try 
            {
                additionalBackgroundDrawing(g2d);
            } 
            catch (Exception ex) 
            {
                logger.log(Level.SEVERE, null, ex);
            }
            
            g2d.drawString(message, x, y);
            
            try 
            {
                additionalForegroundDrawing(g2d);
            } 
            catch (Exception ex) 
            {
                logger.log(Level.SEVERE, null, ex);
            }
            
            g2d.dispose();
System.out.println(".");

            ServletContext servletContext = getServletContext();     
            Pixel pixel = (Pixel) servletContext.getAttribute(PIXEL_KEY);
            if(pixel != null)
            {
                try 
                {  
                    pixel.writeImagetoMatrix(img, MATRIX_TYPE.width, MATRIX_TYPE.height);
                } 
                catch (ConnectionLostException ex) 
                {
                    logger.log(Level.SEVERE, null, ex);
                }                
            }
                        
            int messageWidth = fm.stringWidth(message);            
            int resetX = 0 - messageWidth;
            
            if(x == resetX)
            {
                x = w;
            }
            else
            {
                x--;
            }
        }        
    }
}