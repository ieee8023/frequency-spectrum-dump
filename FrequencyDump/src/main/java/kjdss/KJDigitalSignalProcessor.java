/**
 * Project: KJ DSS
 * File   : KJDigitalSignalProcessor.java
 *
 * Author : Kristofer Fudalewski
 * Email  : sirk_sytes@hotmail.com   
 * Website: http://sirk.sytes.net
 */
package kjdss;

import javax.sound.sampled.SourceDataLine;

/**
 * @author Kris Fudalewski
 * 
 * Classes must implement this interface in order to be registered with the 
 * KJDigitalSignalSynchronizer class.
 *  
 */
public interface KJDigitalSignalProcessor {

	/**
	 * Called by the KJDigitalSignalSynchronizer during the call of the 'start' method.
	 * Allows a DSP to prepare any necessary buffers or objects according to the audio format of
	 * the source data line.
	 * 
	 * @param pSampleSize     The sample size that this DSP should be prepared to handle.
	 * @param pSourceDataLine The source data line that will be monitored.
	 */
	void initialize( int pSampleSize, SourceDataLine pSourceDataLine );
	
	/**
	 * Called by the KJDigitalSignalSynchronizer while the SourceDataLine is active.
	 * 
	 * @param pDssContext A context object containing a reference to the sample data to be processed
	 * 					  as well as other useful references during processing time.
	 */
    void process( KJDigitalSignalSynchronizer.Context pDssContext );
	
}
