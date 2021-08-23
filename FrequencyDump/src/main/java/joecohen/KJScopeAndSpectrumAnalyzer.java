/**
 * Project: KJ DSS
 * File   : KJScopeAndSpectrumAnalyzer.java
 *
 * Author : Kristofer Fudalewski
 * Email  : sirk_sytes@hotmail.com   
 * Website: http://sirk.sytes.net
 */
package joecohen;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.SourceDataLine;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

import kjdss.KJDigitalSignalProcessor;
import kjdss.KJDigitalSignalSynchronizer;
import kjdss.KJFFT;

/**
 * @author Kris Fudalewski
 *
 * Using the KJDigitalSignalProcessor package, this component can display a realtime
 * scope, spectrum analyser, or volume meter.
 */
public class KJScopeAndSpectrumAnalyzer extends Component implements KJDigitalSignalProcessor {

	private static final long serialVersionUID = 1L;

	protected static final Font SMALL_FONT = new Font( "fixed", Font.PLAIN, 12 );
	
	public static final BandDistribution BAND_DISTRIBUTION_LINEAR = new LinearBandDistribution();
	public static final BandDistribution BAND_DISTRIBUTION_LOG    = new LogBandDistribution( 4, 20.0 );
	
	public static final BandGain         BAND_GAIN_FLAT           = new FlatBandGain( 4.0f );
	public static final BandGain         BAND_GAIN_FREQUENCY      = new FrequencyBandGain( 4.0f );
	
	public static final int   DISPLAY_MODE_SCOPE             = 0;
	public static final int   DISPLAY_MODE_SPECTRUM_ANALYSER = 1;
	public static final int   DISPLAY_MODE_VU_METER          = 2;
	
	public static final int   DEFAULT_WIDTH  = 256;
	public static final int   DEFAULT_HEIGHT = 128;

	public static final int   DEFAULT_SCOPE_DETAIL_LEVEL = 1;
	
	public static final int              DEFAULT_SPECTRUM_ANALYSER_BAND_COUNT        = 16;
	public static final BandDistribution DEFAULT_SPECTRUM_ANALYSER_BAND_DISTRIBUTION = BAND_DISTRIBUTION_LOG;
	public static final BandGain         DEFAULT_SPECTRUM_ANALYSER_BAND_GAIN         = BAND_GAIN_FREQUENCY;
	public static final float            DEFAULT_SPECTRUM_ANALYSER_DECAY             = 0.03f;
	public static final float            DEFAULT_SPECTRUM_ANALYSER_GAIN              = 1.0f;

	public static final Color DEFAULT_BACKGROUND_COLOR = new Color( 0,   0,   128 );	
	public static final Color DEFAULT_SCOPE_COLOR      = new Color( 255, 192, 0 );
	
	public static final float DEFAULT_VU_METER_DECAY   = 0.02f;
	
	protected Image  doubleBuffer;

	protected int displayMode = DISPLAY_MODE_SCOPE;
	
	protected Color   scopeColor      = DEFAULT_SCOPE_COLOR; 
	protected Color[] colorScaleTable = getDefaultColorScale();
	
	protected int width;
	protected int height;
	protected int height_2;
	
	// -- Scope variables.
	protected int     scopeDetailLevel = DEFAULT_SCOPE_DETAIL_LEVEL;
	
	// -- Spectrum analyser varibles.
	protected float            saColorScale;   
	protected int              saFFTSampleSize;  
	protected float            saFFTSampleRate;  
	protected float            saDecay = DEFAULT_SPECTRUM_ANALYSER_DECAY; 
	protected float            saGain = DEFAULT_SPECTRUM_ANALYSER_GAIN;

	protected int              saBands;   
	protected BandDistribution saBandDistribution = DEFAULT_SPECTRUM_ANALYSER_BAND_DISTRIBUTION;
	protected Band[]           sabdTable;
	protected BandGain         saBandGain = DEFAULT_SPECTRUM_ANALYSER_BAND_GAIN;
	public float[]          sabgTable;
	public DescriptiveStatistics[] stats = new DescriptiveStatistics[255];
	public float[]          freqs;
	
	protected float            saBandWidth;
	protected boolean          saShowFrequencies = true;
	
	protected KJFFT   fft; 
	protected float[] old_FFT;
	
	// -- VU Meter
	float[] oldVolume;

	protected float vuDecay = DEFAULT_VU_METER_DECAY; 
	protected float vuColorScale;   
	
	// -- FPS calulations.
	private long    lfu = 0;
	private	int     fc  = 0;
	private int     fps;
	private boolean showFPS = false;

	// -- General
	protected boolean toggleDisplayModeEnabled;
	
	public KJScopeAndSpectrumAnalyzer() {
		initialize();
	}
	
	/**
	 * Computes a color scale value for both the spectrum analyzers and volume meter bars.
	 * This ensures the colors are spread uniformly across the width or height of the respective bars.
	 */
	protected void computeColorScale() {
		saColorScale = ( (float)colorScaleTable.length / ( height - 32 ) ) * 2.0f;
		vuColorScale = ( (float)colorScaleTable.length / ( width - 32 ) ) * 2.0f;
	}

	/**
	 * Computes and stores a band distribution and gain tables for the spectrum analyzer. This is 
	 * performed using the current band distribution and gain instances. 
	 * See setSpectrumAnalyzerBandDistribution() or setSpectrumAnalyserBandGain() methods.
	 */
	protected void computeBandTables() {
		
		if ( saBands > 0 && saFFTSampleSize > 0 & fft != null ) {

			// -- Create band table.
			sabdTable = saBandDistribution.create( saBands, fft, saFFTSampleRate );
			saBands   = sabdTable.length;

			updateSpectrumAnalyserBandWidth();
			
			// -- Resolve band descriptions.
			resolveBandDescriptions( sabdTable );
			
			// -- Create gain table.
			sabgTable = saBandGain.create( fft, saFFTSampleRate );
			
			
			//printTable( sabdTable );
			
		}
		
	}

//	protected Image createDoubleBuffer( int pWidth, int pHeight ) {
//		
//		Component wDbh    = this;
//		Component wParent = null;
//		
//		while( ( wParent = wDbh.getParent() ) != null ) {
//			wDbh = wParent;
//		}
//		
//		return wDbh.createImage( pWidth, pHeight );
//		
//	}
	
	/**
	 * @see drawScope
	 */
	protected void drawScope( Graphics pGrp, float[][] pChannels, float pFrrh ) {
		drawScope( pGrp, channelMerge( pChannels ), pFrrh );
	}
	
	/**
	 * Draws a scope of the audio data across the entire width and height of this component.
	 * 
	 * @param pGrp AWT Graphics context.
	 * @param pSample Stereo merged audio data.
	 * @param pFrrh Frame rate ratio hint.
	 */
	protected void drawScope( Graphics pGrp, float[] pSample, float pFrrh ) {
		
		pGrp.setColor( scopeColor );
    	
    	int wLas = (int)( pSample[ 0 ] * (float)height_2 ) + height_2;
    	
       	for ( int a = scopeDetailLevel, c = 0; c < width && a < pSample.length; a += scopeDetailLevel, c += scopeDetailLevel ) {
    		
    		int wAs = (int)( pSample[ a ] * (float)height_2 ) + height_2;
    		
//    		pGrp.drawLine( 
//    		    c, 
//				wLas, 
//				c, 
//				wLas );
    		
    		pGrp.drawLine( 
        		    c, 
    				wLas, 
    				c + scopeDetailLevel, 
    				wAs );
    		
    		wLas = wAs;
    		
    	}
	       	
	}

	/**
	 * @see drawSpectrumAnalyzer
	 */
	protected void drawSpectrumAnalyser( Graphics pGrp, float[][] pChannels, float pFrrh ) {
		drawSpectrumAnalyser( pGrp, channelMerge( pChannels ), pFrrh );
	}
	
	/**
	 * Draws a spectrum analyzer across the entire width and height if this component.
	 * 
	 * @param pGrp AWT Graphics context.
	 * @param pSample Stereo merged audio data.
	 * @param pFrrh Frame rate ratio hint.
	 */
	protected void drawSpectrumAnalyser( Graphics pGrp, float[] pSample, float pFrrh ) {
		
    	float c = 16; // lc = 16 - saBandWidth;
    	float wSadfrr = ( saDecay * pFrrh );
		
    	int b, bd, i, li = 0, mi;
		float fs, m;
		
		int wBm = 1; 
		
		// -- Preparation used for rendering band frequencies.
		if ( saShowFrequencies ) {
			
			if (pGrp != null)
				pGrp.setFont( SMALL_FONT );
			
			wBm = Math.round( 32.0f / saBandWidth );
			
			if ( wBm == 0 ) {
				wBm = 1;
			}
		}
	
		// -- FFT processing.
	   	float[] wFFT = fft.calculate( pSample );
    	
	   	
	   	//freqs = saBands;
    	//for ( bd = 0; bd < saBands; bd++ )
    	//	System.out.print(sabdTable[ bd ].frequency + ",");
    	//System.out.println();
	   	
	   	
	 	// -- Group up available bands using band distribution table.
    	for ( bd = 0; bd < saBands; bd++ ) {

    		// -- Get band distribution entry.
    		i = sabdTable[ bd ].distribution;

    		m = 0;
    		mi = 0;
    		
    		// -- Find loudest band in group. (Group is from 'li' to 'i')
    		for( b = li; b < i; b++ ) {
    			
    			float lf = wFFT[ b ];
    			
    			if ( lf > m ) {
    				m = lf;
    				mi = b;
    			}
    			
    		}

    		li = i;
    		
    		// -- Calculate gain using log, then static gain.
    		fs = ( m * sabgTable[ mi ] ) * saGain;
    		
    		// -- Limit over-saturation.
    		if ( fs > 1.0f ) { 
    			fs = 1.0f; 
    		}
    		
    		// record data  - added by joseph cohen
    		stats[bd].addValue(fs);
    		
    		
    		//sdasd
    		//System.out.print(fs + ",");
    		
    		
    		// -- Compute decay.
    		if ( fs >= ( old_FFT[ bd ] - wSadfrr ) ) {
    			
    			old_FFT[ bd ] = fs;
    			
    		} else {
    			
    			old_FFT[ bd ] -= wSadfrr;
    			
    			if ( old_FFT[ bd ] < 0 ) {
    				old_FFT[ bd ] = 0;
    			}
    			
    			fs = old_FFT[ bd ];
    			
    		}

    		//int wRc = Math.round( c );
    		
    		// -- Draw band.
    		if (pGrp != null)
	    		drawSpectrumAnalyserBand( 
	    			pGrp, 
	    			Math.round( c ), 
					height - 16, 
					Math.round( c + saBandWidth ) - Math.round( c ) - 1, 
					(int)( fs * ( height - 32 ) ),
					sabdTable[ bd ],
					saShowFrequencies && ( bd % wBm ) == 0 );
    		
    //		System.out.println( bd + " " + Math.round( c ) + " " + Math.round( ( Math.round( c + saBandWidth ) - c ) ) + " " + c + " " + lc + " " + saBandWidth ); 
    //		System.out.println( bd + " " + wBm + " " + ( bd % wBm ) + " " + saBandWidth ); 
    		
    //		lc = c;
    		
    		c += saBandWidth;
    		
    	}
    	
	}
	
	/**
	 * @param pGrp
	 * @param pLeft
	 * @param pRight
	 * @param pFrrh
	 */
	protected void drawVolumeMeter( Graphics pGrp, float[][] pChannels, float pFrrh ) {
	
		float[] wVolume = new float[ pChannels.length ];
		
    	float wSadfrr = ( vuDecay * pFrrh );

    	int wHeight = ( height - 32 - ( ( pChannels.length - 1 ) * 8 ) ) / pChannels.length;
		int wY      = 16;
		
		for( int a = 0; a < pChannels.length; a++ ) {
			
			for( int b = 0; b < pChannels[ a ].length; b++ ) {
				
				float wAmp = Math.abs( pChannels[ a ][ b ] );
				
				if ( wAmp > wVolume[ a ] ) {
					wVolume[ a ] = wAmp;
				}
				
			}
			
			if ( wVolume[ a ] >= ( oldVolume[ a ] - wSadfrr ) ) {
				oldVolume[ a ] = wVolume[ a ];
			} else {

				oldVolume[ a ] -= wSadfrr;
				
				if ( oldVolume[ a ] < 0 ) {
					oldVolume[ a ] = 0;
				}
				
			}
			
			drawVolumeMeterBar( pGrp, 16, wY, (int)( oldVolume[ a ]  * (float)( width - 32 ) ), wHeight );

			wY += wHeight + 8;
			
		}
		
	}
	
	/**
	 * Draws a single spectrum analyzer band on this component and the specified coordinates.
	 * 
	 * @param pGraphics AWT Graphics context 
	 * @param pX The X position to draw.
	 * @param pY The Y position to draw.
	 * @param pWidth The width of the band to draw.
	 * @param pHeight The height of the band to draw.
	 */
	protected void drawSpectrumAnalyserBand( Graphics pGraphics, int pX, int pY, int pWidth, int pHeight, Band pBandInfo, boolean pRenderFrequency ) {
	
		float c = 0;
		
		for( int a = pY; a >= pY - pHeight; a -= 2 ) {
			
			c += saColorScale;
			
			if ( c < 256.0f ) {
				pGraphics.setColor( colorScaleTable[ (int)c ] );
			}
			
			pGraphics.fillRect( 
				pX, 
				a, 
				pWidth, 
				1 );
			
		}

		if ( pRenderFrequency ) {
		
			pGraphics.setColor( getForeground() );
			
			int wSx = pX + ( ( pWidth - pGraphics.getFontMetrics().stringWidth( pBandInfo.description ) ) >> 1 );

			pGraphics.drawLine( pX + ( pWidth >> 1 ), pY + 2, pX + ( pWidth >> 1 ), pY + ( pGraphics.getFontMetrics().getHeight() - pGraphics.getFontMetrics().getAscent() ) );
			pGraphics.drawString( pBandInfo.description, wSx, pY + pGraphics.getFontMetrics().getHeight() );
			
		}
		
	}

	/**
	 * Draws a volume meter bar on this component at the specified coordinates.
	 * 
	 * @param pGraphics AWT Graphics context 
	 * @param pX The X position to draw.
	 * @param pY The Y position to draw.
	 * @param pWidth The width of the bar to draw.
	 * @param pHeight The height of the bar to draw.
	 */
	protected void drawVolumeMeterBar( Graphics pGraphics, int pX, int pY, int pWidth, int pHeight ) {
		
		float c = 0;
		
		for( int a = pX; a <= pX + pWidth; a += 2 ) {
			
			c += vuColorScale;
			
			if ( c < 256.0f ) {
				pGraphics.setColor( colorScaleTable[ (int)c ] );
			}
			
			pGraphics.fillRect( 
				a, 
				pY, 
				1, 
				pHeight );
			
		}
		
	}
	
	/**
	 * @return Returns the color scale used to render the spectrum analyser bars.
	 */
	public Color[] getColorScale() {
		return colorScaleTable;
	}
	
	/**
	 * Returns the double buffer used to draw off-screen.
	 * 
	 * @return Image representing an off-screen buffer.
	 */
	protected Image getDoubleBuffer() {
		
		// -- If the buffer is not created OR the size has changed, synch, double check.
		if ( doubleBuffer == null || ( doubleBuffer.getWidth( null ) != getSize().width || doubleBuffer.getHeight( null ) != getSize().height ) ) {
			
			synchronized( getTreeLock() ) {
			
				// -- Check again.
				if ( doubleBuffer == null || ( doubleBuffer.getWidth( null ) != getSize().width || doubleBuffer.getHeight( null ) != getSize().height ) ) {
				
					width    = getSize().width;
					height   = getSize().height;
					height_2 = height >> 1;
					
					updateSpectrumAnalyserBandWidth();
			
					computeColorScale();
					
					doubleBuffer = createImage( 
						width,
						height );
					
				}
			
			}
		
		}
		
		return doubleBuffer;
	
	}
	
	/**
	 * Creates a color array containing the default color spread of the spectrum analyzer and vu meter.
	 * 
	 * @return Default color scale array.
	 */
	protected Color[] getDefaultColorScale() {
		
		Color[] wColors = new Color[ 256 ];
		
		for( int a = 0; a < 128; a++ ) {
			wColors[ a ] = new Color( 0, ( a >> 1 ) + 192, 0 );
		}
		
		for( int a = 0; a < 64; a++ ) {
			wColors[ a + 128 ] = new Color( a << 2, 255, 0 );
		}

		for( int a = 0; a < 64; a++ ) {
			wColors[ a + 192 ] = new Color( 255, 255 - ( a << 2 ), 0 );
		}
		
		return wColors;
		
	}
	
	/**
	 * @return Returns the current display mode, DISPLAY_MODE_SCOPE or DISPLAY_MODE_SPECTRUM_ANALYSER.
	 */
	public int getDisplayMode() {
		return displayMode;
	}
	
	/**
	 * @return Returns the current number of bands displayed by the spectrum analyser.  
	 */
	public int getSpectrumAnalyserBandCount() {
		return saBands;
	}
	
	/**
	 * @return Returns the current band distribution type for the spectrum analyser.  
	 */
	public BandDistribution getSpectrumAnalyserBandDistribution() {
		return saBandDistribution;
	}
	
	/**
	 * @return Returns the current band distribution table for the spectrum analyser.  
	 */
	public Band[] getSpectrumAnalyserBandDistributionTable() {
		return sabdTable;
	}
	
	/**
	 * @return Returns 'true' if band frequencies are being rendered.
	 */
	public boolean getSpectrumAnalyserBandFrequenciesVisible() {
		return saShowFrequencies;
	}
	
	/**
	 * @return Returns the current band gain type for the spectrum analyser.  
	 */
	public BandGain getSpectrumAnalyserBandGain() {
		return saBandGain;
	}
	
	/**
	 * @return Returns the current band gain table for the spectrum analyser.  
	 */
	public float[] getSpectrumAnalyserBandGainTable() {
		return sabgTable;
	}
	
	/**
	 * @return Returns the decay rate of the spectrum analyser's bands. 
	 */
	public float getSpectrumAnalyserDecay() {
		return saDecay;
	}
	
	/**
	 * @return Returns the gain applied to the spectrum analyser's bands. 
	 */
	public float getSpectrumAnalyserGain() {
		return saGain;
	}
	
	/**
	 * @return Returns the color the scope is rendered in.
	 */
	public Color getScopeColor() {
		return scopeColor;
	}
	
	/**
	 * @return Returns the detail level of the scope.
	 */
	public int getScopeDetailLevel() {
		return scopeDetailLevel;
	}
	
	/**
	 * @return Returns the decay rate of the volume meter. 
	 */
	public float getVolumeMeterDecay() {
		return vuDecay;
	}
	
	protected void initialize() {
		
		setSize( DEFAULT_WIDTH, DEFAULT_HEIGHT );

		setBackground( DEFAULT_BACKGROUND_COLOR );
		
		prepareDisplayToggleListener();

		setSpectrumAnalyserBandCount( DEFAULT_SPECTRUM_ANALYSER_BAND_COUNT );

		setDisplayModeToggleOnMouseClickEnabled( true );
		
		//init stats
		for (int i = 0; i<stats.length;i++)
			stats[i] = new DescriptiveStatistics();
		
	}
	
	/**
	 * @see kj.dss.KJDigitalSignalProcessor#initialize( int, SourceDataLine )
	 */
	public void initialize( int pSampleSize, SourceDataLine pSourceDataLine ) {
		
		setSpectrumAnalyserSampleSizeAndRate( 
			pSampleSize,
			pSourceDataLine.getFormat().getSampleRate() );
		
		oldVolume = new float[ pSourceDataLine.getFormat().getChannels() ];
		
	}
	
	/**
	 * @return Returns 'true' if the display mode is toggled on mouse click.
	 */
	public boolean isDisplayModeToggledOnMouseClickEnabled() {
		return toggleDisplayModeEnabled;
	}
	
	/**
	 * @return Returns 'true' if "Frames Per Second" are being calculated and displayed.  
	 */
	public boolean isShowingFPS() {
		return showFPS;
	}
	
	public void paint( Graphics pGraphics ) {
		pGraphics.drawImage( getDoubleBuffer(), 0, 0, null );
	}
	
	protected void prepareDisplayToggleListener() {
		
		addMouseListener(
			new MouseAdapter() {
				public void mouseClicked( MouseEvent pEvent ) {
					
					if ( toggleDisplayModeEnabled && pEvent.getButton() == MouseEvent.BUTTON1 ) {
					
						if ( displayMode + 1 > 2 ) {
							displayMode = 0;
						} else { 
							displayMode++;
						}
						
					}
					
				}
			}
	    );
		
	}
	
	/**
	 * @see kj.dss.KJDigitalSignalProcessor#process( KJDigitalSignalSynchronizer.Context )
	 */
	public void process( KJDigitalSignalSynchronizer.Context pDssContext ) {

		float[][] wChannels = pDssContext.getDataNormalized();

		Image wDb = getDoubleBuffer();
		
		Graphics wGrp = null;
		if (wDb != null){
	    	wGrp = wDb.getGraphics(); 
	
	    	wGrp.setColor( getBackground() );
	    	wGrp.fillRect( 0, 0, getSize().width, getSize().height );
		}

    	drawSpectrumAnalyser( wGrp, wChannels, pDssContext.getFrameRatioHint() );


    	
    	// -- Show FPS if necessary.
    	if ( showFPS ) {
    	
	    	// -- Calculate FPS.
			if ( System.currentTimeMillis() >= lfu + 1000 ) {
				
				lfu = System.currentTimeMillis();
				fps = fc;
				fc = 0;
				
			}
	
			fc++;
	    	
	    	wGrp.setColor( Color.yellow );
	    	wGrp.drawString( "FPS: " + fps + " (FRRH: " + pDssContext.getFrameRatioHint() + ")", 0, height - 1 );
	    	
    	}
    	if (wDb != null)
    		getGraphics().drawImage( wDb, 0, 0, null );
    	
    }

	protected void resolveBandDescriptions( Band[] pBandTable ) {
		
		DecimalFormat wDf = new DecimalFormat( "###.#" );
		
		for( Band wBand : pBandTable ) {
			
			if ( wBand.frequency >= 1000.0f ) {
				wBand.description = wDf.format( wBand.frequency / 1000.0f ) + "k";
			} else {
				wBand.description = wDf.format( wBand.frequency );
			}
			
		}
		
		
	}
	
	/**
	 * Sets the current display mode.
	 * 
	 * @param pMode Must be either DISPLAY_MODE_SCOPE or DISPLAY_MODE_SPECTRUM_ANALYSER.
	 */
	public synchronized void setDisplayMode( int pMode ) {
		displayMode = pMode;
	}
	
	/**
	 * Enable or disable the display toggle to the next mode on a mouse click.
	 * 
	 * @param pState True to enable, or false to disable.
	 */
	public void setDisplayModeToggleOnMouseClickEnabled( boolean pState ) {
		
		toggleDisplayModeEnabled = pState;
		
		if ( pState ) {
			setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
		} else {
			setCursor( Cursor.getDefaultCursor() );
		}
		
	}
	
	/**
	 * Sets the color of the scope.
	 * 
	 * @param pColor 
	 */
	public synchronized void setScopeColor( Color pColor ) {
		scopeColor = pColor;
	}

	/**
	 * Sets the detail level of the scope.
	 * 
	 * @param pDetailLevel 
	 */
	public synchronized void setScopeColor( int pDetailLevel ) {
		scopeDetailLevel = pDetailLevel;
	}
	
	/**
	 * When 'true' is passed as a parameter, will overlay the "Frames Per Seconds"
	 * achieved by the component.
	 * 
	 * @param pState
	 */
	public synchronized void setShowFPS( boolean pState ) {
		showFPS = pState;
	}
	
	/**
	 * Sets the numbers of bands rendered by the spectrum analyser. 
	 * 
	 * @param pCount Cannot be more than half the "FFT sample size".
	 */
	public synchronized void setSpectrumAnalyserBandCount( int pCount ) {
		
		saBands = pCount;
		
		computeBandTables();
		
	}
	
	/**
	 * Sets the band distribution type for the spectrum analyser. Band distribution refers to 
	 * how all bands in the spectrum will be grouped together into a smaller number of bands.
	 * There are two available type by default: BAND_DISTRIBUTION_LINEAR, BAND_DISTRIBUTION_LOG
	 * but custom band distributions can be create using the BandDistribution inner inteferace.
	 * 
	 * @param pBandDistribution A band distribution type.
	 */
	public synchronized void setSpectrumAnalyserBandDistribution( BandDistribution pBandDistribution ) {
		
		saBandDistribution = pBandDistribution;
		
		computeBandTables();
		
	}
	
	/**
	 * Shows or hides the rendering of the frequencies for each of the spectrum analyser bands. 
	 * 
	 * @param pState 'True' to render band frquencies, false to hide them.
	 */
	public synchronized void setSpectrumAnalyserBandFrequenciesVisible( boolean pState ) {
		saShowFrequencies = pState;
	}
	
	/**
	 * Sets the band gain type for the spectrum analyser. Band gain refers to how much gain is applied
	 * to each raw frequency band (or bin) returned from FFT processing.
	 * There are two available type by default: BAND_GAIN_FREQUENCY, BAND_GAIN_FLAT
	 * but custom band gains can be create using the BandGain inner inteferace.
	 * 
	 * @param pBandGain A band gain type.
	 */
	public synchronized void setSpectrumAnalyserBandGain( BandGain pBandGain ) {
		
		saBandGain = pBandGain;
		
		computeBandTables();
		
	}
	
	/**
	 * Sets the spectrum analyser band decay rate.
	 * 
	 * @param pDecay Must be a number between 0.0 and 1.0 exclusive.
	 */
	public synchronized void setSpectrumAnalyserDecay( float pDecay ) {
		saDecay = pDecay;
	}
	
	/**
	 * Sets the spectrum analyser band gain.
	 * 
	 * @param pGain This is a multiplier value, default is 2.0
	 */
	public synchronized void setSpectrumAnalyserGain( float pGain ) {
		saGain = pGain;
	}
	
	/**
	 * Sets the color scale for the spectrum analyser and vu meter.
	 * 
	 * @param pColors Any amount of colors may be used. Must not be null.
	 */
	public synchronized void setColorScale( Color[] pColors ) {
		
		colorScaleTable = pColors;
		
		computeColorScale();
		
	}
	
	/**
	 * Sets the FFT sample size and rate to be just for calculating the spectrum analyser values.  
	 * 
	 * @param pSize Cannot be more than the size of the sample provided by the DSP.  
	 */
	protected synchronized void setSpectrumAnalyserSampleSizeAndRate( int pSize, float pRate ) {
		
//		System.out.println( "SS: " + pSize + " SR: " + pRate );
		
		saFFTSampleSize = pSize;
		saFFTSampleRate = pRate;
		
		fft = new KJFFT( saFFTSampleSize );
		old_FFT = new float[ saBands ]; 
		
		computeBandTables();
		
	}
	
	/**
	 * Sets the volume meter decay rate.
	 * 
	 * @param pDecay Must be a number between 0.0 and 1.0, exclusive.
	 */
	public synchronized void setVolumeMeterDecay( float pDecay ) {
		vuDecay = pDecay;
	}
	
	/**
	 * Merges two audio channels into one.
	 * 
	 * @param pLeft Left channel data.
	 * @param pRight Right channel data.
	 * 
	 * @return Merged results of the supplied left and right channel data.
	 */
	protected float[] channelMerge( float[][] pChannels ) {
		
		for( int a = 0; a < pChannels[ 0 ].length; a++ ) {
			
			float wMcd = 0;
			
			for( int b = 0; b < pChannels.length; b++ ) {
				wMcd += pChannels[ b ][ a ];
			}
			
			pChannels[ 0 ][ a ] = wMcd / (float)pChannels.length;
			
		}
		
		return pChannels[ 0 ];
		
	}
	
	public void update( Graphics pGraphics ) {
		// -- Prevent AWT from clearing background.
		paint( pGraphics );
	}
	
    protected void updateSpectrumAnalyserBandWidth() {
    	saBandWidth = (float)( width - 32 ) / (float)saBands;
	}
	
	private static void printTable( Band[] pTable ) {

		int i = 0;
		
		for( Band wBand : pTable ) {
		
			if ( wBand != null ) {
			
				System.out.println( 
					"TB :" + 
					i + " " + pTable[ i ].distribution + " " + 
					pTable[ i ].startFrequency + " " +
					pTable[ i ].endFrequency ); // + " " +
					//pTable[ i ].gain );
				
			} else {
				
				System.out.println( "TB : " + i + " NULL" ); 
				
			}
		
			i++;
			
		}

	}
	
	/**
	 * Interface for band distribution types. Band distribution refers to combining band data
	 * into groups therefore reducing the number of visible bands. For example, a traditional 
	 * 10 band spectrum analyzer contains only 10 visible frequency bands sampled from a 
	 * potentially more than hundreds or more frequency bands. In order to distribute the bands
	 * into only 10, several different distributions can be typically be used such as log or 
	 * simply linear distribution.  
	 * 
	 * #see KJScopeSpectrumAnalyzer$LinearBandDistribution
	 * #see KJScopeSpectrumAnalyzer$LogBandDistribution
	 * 
	 * @author cwnl-kfudalew
	 */
	public static interface BandDistribution {
		
		/**
		 * @param pBandCount  The desired number of visible bands.
		 * @param pFFT        The FFT instance used for the spectrum analyser.
		 * @param pSampleRate The sample rate of the data to process.
		 * 
		 * @return A band distribution table.
		 */
		Band[] create( int pBandCount, KJFFT pFFT, float pSampleRate );
		
	}
	
	/**
	 * Linear based band distribution class.
	 * 
	 * @author cwnl-kfudalew
	 */
	public static class LinearBandDistribution implements BandDistribution {

		public Band[] create( int pBandCount, KJFFT pFFT, float pFFTSampleRate ) {

			// -- We actually only use half of the available data because the higher bands are 
			//    not audible by humans.
			int wOss = pFFT.getOutputSampleSize();
			
			int r = (int)( (double)wOss / (double)pBandCount );
			
			// -- Create a frequency table.
			float[] wFqt = pFFT.calculateFrequencyTable( pFFTSampleRate );
			float   wLfq = 0.0f;
			
			Band[] wSabdTable = new Band[ pBandCount ];
			
			int wBand = 0;
			
			for( double a = r; a <= wOss && wBand < pBandCount; a += r ) {
				
				// -- Build band instance with distribution, frequency range, and gain info.
				wSabdTable[ wBand ] = new Band( 
					(int)a, 
					wLfq, 
					wFqt[ (int)a - r ] );
					//(float)Math.log( wLfq + 2.0 ) );
			
				wLfq = wFqt[ (int)a - r ];

				wBand++;
			
				
			}
			
			return wSabdTable;
			
		}
		
	}
	
	/**
	 * Log based band distribution class.
	 * 
	 * @author cwnl-kfudalew
	 */
	public static class LogBandDistribution implements BandDistribution {

		private double lso;
		private int    sso;
		
		/**
		 * Create a log band distribution instance supplying a sub-sonic offset and a log scale offset.
		 * The sub-sonic offset allows the first 'n' most bands to be combined into the first band group,
		 * while the remaining bands will follow the log distribution curve. The log scale offset refers
		 * to at what point in the log scale to use for distribution calculations. The lower the number, the
		 * few bands per group for the first few band groups.
		 * 
		 * @param pSubSonicOffset Groups the first 'n' bands into the sub-sonic band group. (default: 5)
		 * @param pLogScaleOffset Starting point on the log scale. (default: 20.0)
		 */
		public LogBandDistribution( int pSubSonicOffset, double pLogScaleOffset ) {
			sso = pSubSonicOffset;
			lso = pLogScaleOffset;
		}
		
		public Band[] create( int pBandCount, KJFFT pFFT, float pFFTSampleRate ) {

			// -- Check the output size from the FFT instance to build the band table. 
			int wHss = pFFT.getOutputSampleSize() - sso;
			
			double o = Math.log( lso );
			double r = (double)( pBandCount - 1 ) / ( Math.log( wHss + lso ) - o );
			
	//		System.out.println( "OSS: " + pFFT.getOutputSampleSize() + "lso: " + lso + " O: " + o + " R: " + r );
			
			// -- Create a frequency table.
			float[] wFqt = pFFT.calculateFrequencyTable( pFFTSampleRate );

			float  wLfq = wFqt[ sso ];
			int    wLcb = 1;
			
			List<Band> wBands = new ArrayList<Band>();

			// -- Subsonic bands group.
			wBands.add( new Band( sso, 0, wLfq ) ); //, (float)Math.log( wLfq / 2.0 ) ) );
			
			// -- Divid reset of bands using log.
			for( int b = 0; b < wHss; b++ ) {
				
				// -- Calculate current band.
				double wCb = ( ( Math.log( (double)b + lso ) - o ) * r ) + 1.0;  

				if ( Math.round( wCb ) != wLcb ) {

	//				System.out.println( "AB: " + wCb + " " + wBands.size() + " -> " + ( b + sso ) );
					
					wBands.add( 
						new Band( 
							b + sso, 
							wLfq, 
							wFqt[ b + sso ] ) ); 
	//						(float)Math.log( ( wFqt[ b + sso ] - ( ( wFqt[ b + sso ] - wLfq ) / 2.0 ) ) ) ) );
	//						(float)Math.log( ( b + sso ) + 2 ) );
						
					wLfq = wFqt[ b + sso ];

					wLcb = (int)Math.round( wCb );
					
				}
				
			}

			// -- Fill in last entry if necessary.
			if ( wBands.size() < pBandCount ) {
				
				wBands.add( 
					new Band( 
						( wHss - 1 ) + sso, 
						wLfq, 
						wFqt[ ( wHss - 1 ) + sso ] ) ); 
					//	(float)Math.log( wLfq + 2 ) ) );
				
			}
		
			return wBands.toArray( new Band[ wBands.size() ] );
				
		}
		
	}
	
	public static class Band {

		public int   distribution;

		public float frequency;
		
		public float startFrequency;
		public float endFrequency;
		
		public String description;
		
		public Band( int pDistribution, float pStartFrequency, float pEndFrquency ) {
			
			distribution = pDistribution;
			
			startFrequency = pStartFrequency;
			endFrequency = pEndFrquency;
			
			frequency = pStartFrequency + ( ( pEndFrquency - pStartFrequency ) / 2.0f );
			
		}
		
	}

	/**
	 * Interface for band gain types. Band gain refers to gain applied to each band (or bin) of the 
	 * frequency spectrum generated by FFT processing. Typically lower frequencies generally have
	 * more power than high ones. Therefore a graphical representation of a particular sequence of
	 * audio will tend to show mostly lower fequency bands. The purpose of the BandGain interface is
	 * to allow developers to control the level of gain applied to each band to provide a most even
	 * looking spectrum output.
	 * 
	 * BainGain instances must return a table of gain values to apply to all bands return from FFT
	 * processing. Table returned from the create method must be no more or less than 
	 * pFFT.getOutputSampleSize(); 
	 * 
	 * #see KJScopeSpectrumAnalyzer$FlatBandGain
	 * #see KJScopeSpectrumAnalyzer$FrequencyBandGain
	 * 
	 * @author cwnl-kfudalew
	 */
	public static interface BandGain {
		
		/**
		 * @param pFFT        The FFT instance used for the spectrum analyser.
		 * @param pSampleRate The sample rate of the data to process.
		 * 
		 * @return A band gain table.
		 */
		float[] create( KJFFT pFFT, float pSampleRate );
		
	}
	
	/**
	 * Flat band gain. No extra gain is applied to bands extra for the master gain.
	 * 
	 * @author cwnl-kfudalew
	 */
	public static class FlatBandGain implements BandGain {

		private float gain;
		
		public FlatBandGain( float pGain ) {
			gain = pGain;
		}
		
		public float[] create( KJFFT pFFT, float pFFTSampleRate ) {

			int wOss = pFFT.getOutputSampleSize();
			
			float[] wSabgTable = new float[ wOss ];
			
			for( int i = 0; i < wOss; i++ ) {
				wSabgTable[ i ] = gain;
			}
			
			return wSabgTable;
			
		}
		
	}
	
	/**
	 * Frequency based band gain. More gain is applied as the band frequency increases.
	 * 
	 * @author cwnl-kfudalew
	 */
	public static class FrequencyBandGain implements BandGain {

		private float bias;
		
		/**
		 * @param pBias       A level of bias to flaten out the gain curve. The high the number the
		 *                    less gain is applied to high frequencies.
		 */
		public FrequencyBandGain( float pBias ) {
			
			bias = pBias;
			
		}
		
		public float[] create( KJFFT pFFT, float pFFTSampleRate ) {

			int wOss = pFFT.getOutputSampleSize();
			
			// -- Create a frequency table.
			float[] wFqt = pFFT.calculateFrequencyTable( pFFTSampleRate );
			
			float[] wSabgTable = new float[ wOss ];
			
			for( int i = 0; i < wOss; i++ ) {
				wSabgTable[ i ] = ( ( ( wFqt[ i ] / bias ) + 512.0f ) / 512.0f ) * ( bias * 1.5f );
//				System.out.println( "BGT[" + i + "] = " + wSabgTable[ i ] );
			}
			
			return wSabgTable;
			
		}
		
	}
	
	
}
