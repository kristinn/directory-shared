/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.shared.asn1.ber.tlv;


import org.apache.directory.shared.i18n.I18n;


/**
 * Parse and decode an Integer value.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public final class IntegerDecoder
{
    /** A mask used to get only the necessary bytes */
    private static final int[] MASK = new int[]
        { 0x000000FF, 0x0000FFFF, 0x00FFFFFF, 0xFFFFFFFF };


    /**
     * This is a helper class, there is no reason to define a public constructor for it.
     */
    private IntegerDecoder()
    {
        // Do nothing
    }


    /**
     * Parse a byte buffer and send back an integer, controlling that this number
     * is in a specified interval.
     * 
     * @param value The byte buffer to parse
     * @param min Lowest value allowed, included
     * @param max Highest value allowed, included
     * @return An integer
     * @throws org.apache.directory.shared.asn1.ber.tlv.IntegerDecoderException Thrown if the byte stream does not contains an integer
     */
    public static int parse( Value value, int min, int max ) throws IntegerDecoderException
    {

        int result = 0;

        byte[] bytes = value.getData();

        if ( ( bytes == null ) || ( bytes.length == 0 ) )
        {
            throw new IntegerDecoderException( I18n.err( I18n.ERR_00036_0_BYTES_LONG_INTEGER ) );
        }

        if ( bytes.length > 4 )
        {
            throw new IntegerDecoderException( I18n.err( I18n.ERR_00037_ABOVE_4_BYTES_INTEGER ) );
        }

        for ( int i = 0; ( i < bytes.length ) && ( i < 5 ); i++ )
        {
            result = ( result << 8 ) | ( bytes[i] & 0x00FF );
        }

        if ( ( bytes[0] & 0x80 ) == 0x80 )
        {
            result = -( ( ( ~result ) + 1 ) & MASK[bytes.length - 1] );
        }

        if ( ( result >= min ) && ( result <= max ) )
        {
            return result;
        }
        else
        {
            throw new IntegerDecoderException( I18n.err( I18n.ERR_00038_VALUE_NOT_IN_RANGE, min, max ) );
        }
    }


    /**
     * Parse a byte buffer and send back an integer
     * 
     * @param value The byte buffer to parse
     * @return An integer
     * @throws IntegerDecoderException Thrown if the byte stream does not contains an integer
     */
    public static int parse( Value value ) throws IntegerDecoderException
    {
        return parse( value, Integer.MIN_VALUE, Integer.MAX_VALUE );
    }
}