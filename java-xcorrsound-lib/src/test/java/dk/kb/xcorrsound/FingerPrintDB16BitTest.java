package dk.kb.xcorrsound;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;

class FingerPrintDB16BitTest extends FingerPrintDBNImplTest {
    
    
    public FingerPrintDB16BitTest() {
        super(432, 16);
    }
}
