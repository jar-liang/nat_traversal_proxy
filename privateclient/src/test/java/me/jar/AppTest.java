package me.jar;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue()
    {
        assertTrue( true );
    }

    @Test
    public void testArrayCopy()  {
        byte[] a = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] b = new byte[4];
        System.out.println(Arrays.toString(a));
        System.out.println(Arrays.toString(b));
        System.out.println(" ----------- ");
        System.arraycopy(a, 0, b, 0, 4);
        System.out.println(Arrays.toString(a));
        System.out.println(Arrays.toString(b));
    }
}
