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
package org.apache.directory.shared.ldap.model.name;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.directory.shared.i18n.I18n;
import org.apache.directory.shared.ldap.model.entry.StringValue;
import org.apache.directory.shared.ldap.model.entry.Value;
import org.apache.directory.shared.ldap.model.exception.LdapException;
import org.apache.directory.shared.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.model.schema.AttributeType;
import org.apache.directory.shared.ldap.model.schema.SchemaManager;
import org.apache.directory.shared.ldap.model.schema.normalizers.OidNormalizer;
import org.apache.directory.shared.util.Chars;
import org.apache.directory.shared.util.Hex;
import org.apache.directory.shared.util.StringConstants;
import org.apache.directory.shared.util.Strings;
import org.apache.directory.shared.util.Unicode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class store the name-component part or the following BNF grammar (as of
 * RFC2253, par. 3, and RFC1779, fig. 1) : <br> - &lt;name-component&gt; ::=
 * &lt;attributeType&gt; &lt;spaces&gt; '=' &lt;spaces&gt;
 * &lt;attributeValue&gt; &lt;attributeTypeAndValues&gt; <br> -
 * &lt;attributeTypeAndValues&gt; ::= &lt;spaces&gt; '+' &lt;spaces&gt;
 * &lt;attributeType&gt; &lt;spaces&gt; '=' &lt;spaces&gt;
 * &lt;attributeValue&gt; &lt;attributeTypeAndValues&gt; | e <br> -
 * &lt;attributeType&gt; ::= [a-zA-Z] &lt;keychars&gt; | &lt;oidPrefix&gt; [0-9]
 * &lt;digits&gt; &lt;oids&gt; | [0-9] &lt;digits&gt; &lt;oids&gt; <br> -
 * &lt;keychars&gt; ::= [a-zA-Z] &lt;keychars&gt; | [0-9] &lt;keychars&gt; | '-'
 * &lt;keychars&gt; | e <br> - &lt;oidPrefix&gt; ::= 'OID.' | 'oid.' | e <br> -
 * &lt;oids&gt; ::= '.' [0-9] &lt;digits&gt; &lt;oids&gt; | e <br> -
 * &lt;attributeValue&gt; ::= &lt;pairs-or-strings&gt; | '#' &lt;hexstring&gt;
 * |'"' &lt;quotechar-or-pairs&gt; '"' <br> - &lt;pairs-or-strings&gt; ::= '\'
 * &lt;pairchar&gt; &lt;pairs-or-strings&gt; | &lt;stringchar&gt;
 * &lt;pairs-or-strings&gt; | e <br> - &lt;quotechar-or-pairs&gt; ::=
 * &lt;quotechar&gt; &lt;quotechar-or-pairs&gt; | '\' &lt;pairchar&gt;
 * &lt;quotechar-or-pairs&gt; | e <br> - &lt;pairchar&gt; ::= ',' | '=' | '+' |
 * '&lt;' | '&gt;' | '#' | ';' | '\' | '"' | [0-9a-fA-F] [0-9a-fA-F] <br> -
 * &lt;hexstring&gt; ::= [0-9a-fA-F] [0-9a-fA-F] &lt;hexpairs&gt; <br> -
 * &lt;hexpairs&gt; ::= [0-9a-fA-F] [0-9a-fA-F] &lt;hexpairs&gt; | e <br> -
 * &lt;digits&gt; ::= [0-9] &lt;digits&gt; | e <br> - &lt;stringchar&gt; ::=
 * [0x00-0xFF] - [,=+&lt;&gt;#;\"\n\r] <br> - &lt;quotechar&gt; ::= [0x00-0xFF] -
 * [\"] <br> - &lt;separator&gt; ::= ',' | ';' <br> - &lt;spaces&gt; ::= ' '
 * &lt;spaces&gt; | e <br>
 * <br>
 * A Rdn is a part of a Dn. It can be composed of many types, as in the Rdn
 * following Rdn :<br>
 * ou=value + cn=other value<br>
 * <br>
 * or <br>
 * ou=value + ou=another value<br>
 * <br>
 * In this case, we have to store an 'ou' and a 'cn' in the Rdn.<br>
 * <br>
 * The types are case insensitive. <br>
 * Spaces before and after types and values are not stored.<br>
 * Spaces before and after '+' are not stored.<br>
 * <br>
 * Thus, we can consider that the following RDNs are equals :<br>
 * <br>
 * 'ou=test 1'<br> ' ou=test 1'<br>
 * 'ou =test 1'<br>
 * 'ou= test 1'<br>
 * 'ou=test 1 '<br> ' ou = test 1 '<br>
 * <br>
 * So are the following :<br>
 * <br>
 * 'ou=test 1+cn=test 2'<br>
 * 'ou = test 1 + cn = test 2'<br> ' ou =test 1+ cn =test 2 ' <br>
 * 'cn = test 2 +ou = test 1'<br>
 * <br>
 * but the following are not equal :<br>
 * 'ou=test 1' <br>
 * 'ou=test 1'<br>
 * because we have more than one spaces inside the value.<br>
 * <br>
 * The Rdn is composed of one or more Ava. Those Avas
 * are ordered in the alphabetical natural order : a < b < c ... < z As the type
 * are not case sensitive, we can say that a = A
 * <br>
 * This class is immutable.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class Rdn implements Cloneable, Externalizable, Iterable<Ava>, Comparable<Rdn>
{
    /** The LoggerFactory used by this class */
    protected static final Logger LOG = LoggerFactory.getLogger( Rdn.class );

    /** An empty Rdn */
    public static final Rdn EMPTY_RDN = new Rdn();

    /**
    * Declares the Serial Version Uid.
    *
    * @see <a
    *      href="http://c2.com/cgi/wiki?AlwaysDeclareSerialVersionUid">Always
    *      Declare Serial Version Uid</a>
    */
    private static final long serialVersionUID = 1L;

    /** The User Provided Rdn */
    private String upName = null;

    /** The normalized Rdn */
    private String normName = null;

    /**
     * Stores all couple type = value. We may have more than one type, if the
     * '+' character appears in the Ava. This is a TreeSet,
     * because we want the Avas to be sorted. An Ava may contain more than one
     * value. In this case, the values are String stored in a List.
     */
    private List<Ava> avas = null;

    /**
     * We also keep a set of types, in order to use manipulations. A type is
     * connected with the Ava it represents.
     *
     * Note : there is no Generic available classes in commons-collection...
     */
    private MultiMap avaTypes = new MultiValueMap();

    /**
     * We keep the type for a single valued Rdn, to avoid the creation of an HashMap
     */
    private String avaType = null;

    /**
     * A simple Ava is used to store the Rdn for the simple
     * case where we only have a single type=value. This will be 99.99% the
     * case. This avoids the creation of a HashMap.
     */
    protected Ava ava = null;

    /**
     * The number of Avas. We store this number here to avoid complex
     * manipulation of Ava and Avas
     */
    private int nbAvas = 0;

    /** CompareTo() results */
    public static final int UNDEFINED = Integer.MAX_VALUE;

    /** Constant used in comparisons */
    public static final int SUPERIOR = 1;

    /** Constant used in comparisons */
    public static final int INFERIOR = -1;

    /** Constant used in comparisons */
    public static final int EQUAL = 0;

    /** A flag used to tell if the Rdn has been normalized */
    private boolean normalized = false;

    /** the schema manager */
    private SchemaManager schemaManager;

    /** The computed hashcode */
    private volatile int h;


    /**
     * A empty constructor.
     */
    public Rdn()
    {
        this( ( SchemaManager ) null );
    }


    /**
     *
     * Creates a new schema aware instance of Rdn.
     *
     * @param schemaManager the schema manager
     */
    public Rdn( SchemaManager schemaManager )
    {
        // Don't waste space... This is not so often we have multiple
        // name-components in a Rdn... So we won't initialize the Map and the
        // treeSet.
        this.schemaManager = schemaManager;
        upName = "";
        normName = "";
        normalized = false;
        h = 0;
    }


    /**
     *  A constructor that parse a String representing a schema aware Rdn.
     *
     * @param schemaManager the schema manager
     * @param rdn the String containing the Rdn to parse
     * @throws LdapInvalidDnException if the Rdn is invalid
     */
    public Rdn( SchemaManager schemaManager, String rdn ) throws LdapInvalidDnException
    {
        if ( Strings.isNotEmpty( rdn ) )
        {
            // Parse the string. The Rdn will be updated.
            parse( rdn, this );

            // create the internal normalized form
            // and store the user provided form
            if ( schemaManager != null )
            {
                this.schemaManager = schemaManager;
                apply( schemaManager );
                normalized = true;
            }
            else
            {
                normalize();
                normalized = false;
            }

            if ( upName.length() < rdn.length() )
            {
                throw new LdapInvalidDnException( "Invalid RDN" );
            }
            
            upName = rdn;
        }
        else
        {
            upName = "";
            normName = "";
            normalized = false;
        }

        hashCode();
    }


    /**
     * A constructor that parse a String representing a Rdn.
     *
     * @param rdn the String containing the Rdn to parse
     * @throws LdapInvalidDnException if the Rdn is invalid
     */
    public Rdn( String rdn ) throws LdapInvalidDnException
    {
        this( ( SchemaManager ) null, rdn );
    }


    /**
     * A constructor that constructs a schema aware Rdn from a type and a value.
     * <p>
     * The string attribute values are not interpreted as RFC 414 formatted Rdn
     * strings. That is, the values are used literally (not parsed) and assumed
     * to be un-escaped.
      *
     * @param schemaManager the schema manager
     * @param upType the user provided type of the Rdn
     * @param upValue the user provided value of the Rdn
     * @throws LdapInvalidDnException if the Rdn is invalid
     */
    public Rdn( SchemaManager schemaManager, String upType, String upValue ) throws LdapInvalidDnException
    {
        addAVA( schemaManager, upType, upType, new StringValue( upValue ), new StringValue( upValue ) );

        upName = upType + '=' + upValue;

        if ( schemaManager != null )
        {
            this.schemaManager = schemaManager;
            apply( schemaManager );
            normalized = true;
        }
        else
        {
            // create the internal normalized form
            normalize();

            // As strange as it seems, the Rdn is *not* normalized against the schema at this point
            normalized = false;
        }

        hashCode();
    }


    /**
     * A constructor that constructs a Rdn from a type and a value.
     *
     * @param upType the user provided type of the Rdn
     * @param upValue the user provided value of the Rdn
     * @throws LdapInvalidDnException if the Rdn is invalid
     * @see #Rdn( SchemaManager, String, String )
     */
    public Rdn( String upType, String upValue ) throws LdapInvalidDnException
    {
        this( null, upType, upValue );
    }


    /**
     * Constructs an Rdn from the given rdn. The content of the rdn is simply
     * copied into the newly created Rdn.
     *
     * @param rdn The non-null Rdn to be copied.
     */
    public Rdn( Rdn rdn )
    {
        nbAvas = rdn.size();
        this.normName = rdn.normName;
        this.upName = rdn.getName();
        normalized = rdn.normalized;
        schemaManager = rdn.schemaManager;

        switch ( rdn.size() )
        {
            case 0:
                hashCode();

                return;

            case 1:
                this.ava = rdn.ava.clone();
                hashCode();

                return;

            default:
                // We must duplicate the treeSet and the hashMap
                avas = new ArrayList<Ava>();
                avaTypes = new MultiValueMap();

                for ( Ava currentAva : rdn.avas )
                {
                    avas.add( currentAva.clone() );
                    avaTypes.put( currentAva.getNormType(), currentAva );
                }

                hashCode();

                return;
        }
    }


    /**
     * Transform the external representation of the current Rdn to an internal
     * normalized form where :
     * - types are trimmed and lower cased
     * - values are trimmed and lower cased
     */
    // WARNING : The protection level is left unspecified on purpose.
    // We need this method to be visible from the DnParser class, but not
    // from outside this package.
    /* Unspecified protection */void normalize()
    {
        switch ( nbAvas )
        {
            case 0:
                // An empty Rdn
                normName = "";
                break;

            case 1:
                // We have a single Ava
                // We will trim and lowercase type and value.
                if ( ava.getNormValue().isHumanReadable() )
                {
                    normName = ava.getNormName();
                }
                else
                {
                    normName = ava.getNormType() + "=#" + Strings.dumpHexPairs( ava.getNormValue().getBytes() );
                }

                break;

            default:
                // We have more than one Ava
                StringBuffer sb = new StringBuffer();

                boolean isFirst = true;

                for ( Ava ata : avas )
                {
                    if ( isFirst )
                    {
                        isFirst = false;
                    }
                    else
                    {
                        sb.append( '+' );
                    }

                    sb.append( ata.getNormName() );
                }

                normName = sb.toString();
                break;
        }

        hashCode();
    }


    /**
     * Transform a Rdn by changing the value to its OID counterpart and
     * normalizing the value accordingly to its type.
     *
     * @param schemaManager the SchemaManager
     * @return this Rdn, normalized
     * @throws LdapInvalidDnException if the Rdn is invalid
     */
    public Rdn apply( SchemaManager schemaManager ) throws LdapInvalidDnException
    {
        if ( normalized )
        {
            return this;
        }

        String savedUpName = getName();
        Dn.rdnOidToName( this, schemaManager );
        normalize();
        this.upName = savedUpName;
        normalized = true;
        this.schemaManager = schemaManager;
        hashCode();

        return this;
    }


    /**
     * Add an Ava to the current Rdn
     *
     * @param upType The user provided type of the added Rdn.
     * @param type The normalized provided type of the added Rdn.
     * @param upValue The user provided value of the added Rdn
     * @param value The normalized provided value of the added Rdn
     * @throws LdapInvalidDnException
     *             If the Rdn is invalid
     */
    private void addAVA( SchemaManager schemaManager, String upType, String type, Value<?> upValue,
        Value<?> value ) throws LdapInvalidDnException
    {
        // First, let's normalize the type
        Value<?> normalizedValue = value;
        String normalizedType = Strings.lowerCaseAscii( type );
        this.schemaManager = schemaManager;

        if ( schemaManager != null )
        {
            OidNormalizer oidNormalizer = schemaManager.getNormalizerMapping().get( normalizedType );
            normalizedType = oidNormalizer.getAttributeTypeOid();

            try
            {
                normalizedValue = oidNormalizer.getNormalizer().normalize( value );
            }
            catch ( LdapException e )
            {
                throw new LdapInvalidDnException( e.getMessage(), e );
            }
        }

        switch ( nbAvas )
        {
            case 0:
                // This is the first Ava. Just stores it.
                ava = new Ava( schemaManager, upType, normalizedType, upValue, normalizedValue );
                nbAvas = 1;
                avaType = normalizedType;
                hashCode();

                return;

            case 1:
                // We already have an Ava. We have to put it in the HashMap
                // before adding a new one.
                // First, create the HashMap,
                avas = new ArrayList<Ava>();

                // and store the existing Ava into it.
                avas.add( ava );
                avaTypes = new MultiValueMap();
                avaTypes.put( avaType, ava );

                ava = null;

                // Now, fall down to the commmon case
                // NO BREAK !!!

            default:
                // add a new Ava
                Ava newAva = new Ava( schemaManager, upType, normalizedType, upValue, normalizedValue );
                avas.add( newAva );
                avaTypes.put( normalizedType, newAva );
                nbAvas++;
                hashCode();

                return;

        }
    }


    /**
     * Add an Ava to the current schema aware Rdn
     *
     * @param value The added Ava
     */
    // WARNING : The protection level is left unspecified intentionally.
    // We need this method to be visible from the DnParser class, but not
    // from outside this package.
    /* Unspecified protection */void addAVA( SchemaManager schemaManager, Ava value ) throws LdapInvalidDnException
    {
        this.schemaManager = schemaManager;
        String normalizedType = value.getNormType();

        switch ( nbAvas )
        {
            case 0:
                // This is the first Ava. Just stores it.
                ava = value;
                nbAvas = 1;
                avaType = normalizedType;
                hashCode();

                return;

            case 1:
                // We already have an Ava. We have to put it in the HashMap
                // before adding a new one.
                // Check that the first AVA is not for the same attribute
                if ( avaType.equals( normalizedType ) )
                {
                    throw new LdapInvalidDnException( "Invalid RDN: the " + normalizedType + " is already present in the RDN" );
                }
                
                // First, create the HashMap,
                avas = new ArrayList<Ava>();

                // and store the existing Ava into it.
                avas.add( ava );
                avaTypes = new MultiValueMap();
                avaTypes.put( avaType, ava );

                this.ava = null;

                // Now, fall down to the commmon case
                // NO BREAK !!!

            default:
                // Check that the AT is not already present
                if ( avaTypes.containsKey( normalizedType ) )
                {
                    throw new LdapInvalidDnException( "Invalid RDN: the " + normalizedType + " is already present in the RDN" );
                }
                
                // add a new Ava
                avas.add( value );
                avaTypes.put( normalizedType, value );
                nbAvas++;
                hashCode();

                break;
        }
    }


    /**
     * Clear the Rdn, removing all the Avas.
     */
    // WARNING : The protection level is left unspecified intentionally.
    // We need this method to be visible from the DnParser class, but not
    // from outside this package.
    /* No protection */void clear()
    {
        ava = null;
        avas = null;
        avaType = null;
        avaTypes.clear();
        nbAvas = 0;
        normName = "";
        upName = "";
        normalized = false;
        h = 0;
    }


    /**
     * Get the Value of the Ava which type is given as an
     * argument.
     *
     * @param type the type of the NameArgument
     * @return the Value to be returned, or null if none found.
     * @throws LdapInvalidDnException if the Rdn is invalid
     */
    public Object getValue( String type ) throws LdapInvalidDnException
    {
        // First, let's normalize the type
        String normalizedType = Strings.lowerCaseAscii( Strings.trim( type ) );

        if ( schemaManager != null )
        {
            AttributeType attributeType = schemaManager.getAttributeType( normalizedType );

            if ( attributeType != null )
            {
                normalizedType = attributeType.getOid();
            }
        }

        switch ( nbAvas )
        {
            case 0:
                return "";

            case 1:
                if ( Strings.equals( ava.getNormType(), normalizedType ) )
                {
                    return ava.getNormValue().getValue();
                }

                return "";

            default:
                if ( avaTypes.containsKey( normalizedType ) )
                {
                    @SuppressWarnings("unchecked")
                    Collection<Ava> atavList = ( Collection<Ava> ) avaTypes.get( normalizedType );
                    StringBuffer sb = new StringBuffer();
                    boolean isFirst = true;

                    for ( Ava elem : atavList )
                    {
                        if ( isFirst )
                        {
                            isFirst = false;
                        }
                        else
                        {
                            sb.append( ',' );
                        }

                        sb.append( elem.getNormValue() );
                    }

                    return sb.toString();
                }

                return "";
        }
    }


    /**
     * Get the Ava which type is given as an argument. If we
     * have more than one value associated with the type, we will return only
     * the first one.
     *
     * @param type
     *            The type of the NameArgument to be returned
     * @return The Ava, of null if none is found.
     */
    public Ava getAva( String type )
    {
        // First, let's normalize the type
        String normalizedType = Strings.lowerCaseAscii( Strings.trim( type ) );

        switch ( nbAvas )
        {
            case 0:
                return null;

            case 1:
                if ( ava.getNormType().equals( normalizedType ) )
                {
                    return ava;
                }

                return null;

            default:
                if ( avaTypes.containsKey( normalizedType ) )
                {
                    @SuppressWarnings("unchecked")
                    Collection<Ava> atavList = ( Collection<Ava> ) avaTypes.get( normalizedType );
                    return atavList.iterator().next();
                }

                return null;
        }
    }


    /**
     * Retrieves the components of this Rdn as an iterator of Avas.
     * The effect on the iterator of updates to this Rdn is undefined. If the
     * Rdn has zero components, an empty (non-null) iterator is returned.
     *
     * @return an iterator of the components of this Rdn, each an Ava
     */
    public Iterator<Ava> iterator()
    {
        if ( nbAvas == 1 || nbAvas == 0 )
        {
            return new Iterator<Ava>()
            {
                private boolean hasMoreElement = nbAvas == 1;


                public boolean hasNext()
                {
                    return hasMoreElement;
                }


                public Ava next()
                {
                    Ava obj = ava;
                    hasMoreElement = false;
                    return obj;
                }


                public void remove()
                {
                    // nothing to do
                }
            };
        }
        else
        {
            return avas.iterator();
        }
    }


    /**
     * Clone the Rdn
     *
     * @return A clone of the current Rdn
     */
    public Rdn clone()
    {
        try
        {
            Rdn rdn = ( Rdn ) super.clone();
            rdn.normalized = normalized;

            // The Ava is immutable. We won't clone it

            switch ( rdn.size() )
            {
                case 0:
                    break;

                case 1:
                    rdn.ava = this.ava.clone();
                    rdn.avaTypes = avaTypes;
                    break;

                default:
                    // We must duplicate the treeSet and the hashMap
                    rdn.avaTypes = new MultiValueMap();
                    rdn.avas = new ArrayList<Ava>();

                    for ( Ava currentAva : this.avas )
                    {
                        rdn.avas.add( currentAva.clone() );
                        rdn.avaTypes.put( currentAva.getNormType(), currentAva );
                    }

                    break;
            }

            return rdn;
        }
        catch ( CloneNotSupportedException cnse )
        {
            throw new Error( "Assertion failure" );
        }
    }


    /**
     * @return the user provided name
     */
    public String getName()
    {
        return upName;
    }


    /**
     * @return The normalized name
     */
    public String getNormName()
    {
        return normName == null ? "" : normName;
    }


    /**
     * Set the User Provided Name.
     *
     * Package private because Rdn is immutable, only used by the Dn parser.
     *
     * @param upName the User Provided dame
     */
    void setUpName( String upName )
    {
        this.upName = upName;
    }


    /**
     * Return the unique Ava, or the first one of we have more
     * than one
     *
     * @return The first Ava of this Rdn
     */
    public Ava getAva()
    {
        switch ( nbAvas )
        {
            case 0:
                return null;

            case 1:
                return ava;

            default:
                return avas.get( 0 ).clone();
        }
    }


    /**
     * Return the user provided type, or the first one of we have more than one (the lowest)
     *
     * @return The first user provided type of this Rdn
     */
    public String getType()
    {
        switch ( nbAvas )
        {
            case 0:
                return null;

            case 1:
                return ava.getType();

            default:
                return avas.get( 0 ).getType();
        }
    }


    /**
     * Return the normalized type, or the first one of we have more than one (the lowest)
     *
     * @return The first normalized type of this Rdn
     */
    public String getNormType()
    {
        switch ( nbAvas )
        {
            case 0:
                return null;

            case 1:
                return ava.getNormType();

            default:
                return avas.get( 0 ).getNormType();
        }
    }


    /**
     * Return the User Provided value
     *
     * @return The first User provided value of this Rdn
     */
    public Value<?> getValue()
    {
        switch ( nbAvas )
        {
            case 0:
                return null;

            case 1:
                return ava.getValue();

            default:
                return avas.get( 0 ).getValue();
        }
    }


    /**
     * Return the normalized value, or the first one of we have more than one (the lowest)
     *
     * @return The first normalized value of this Rdn
     */
    public Value<?> getNormValue()
    {
        switch ( nbAvas )
        {
            case 0:
                return null;

            case 1:
                return ava.getNormValue();

            default:
                return avas.get( 0 ).getNormValue();
        }
    }


    /**
     * Compares the specified Object with this Rdn for equality. Returns true if
     * the given object is also a Rdn and the two Rdns represent the same
     * attribute type and value mappings. The order of components in
     * multi-valued Rdns is not significant.
     *
     * @param rdn
     *            Rdn to be compared for equality with this Rdn
     * @return true if the specified object is equal to this Rdn
     */
    public boolean equals( Object that )
    {
        if ( this == that )
        {
            return true;
        }

        if ( !( that instanceof Rdn ) )
        {
            return false;
        }

        Rdn rdn = ( Rdn ) that;
        
        // Short cut : compare the normalized Rdn
        if ( normName.equals( rdn.normName ) )
        {
            return true;
        }

        // Short cut : compare the normalized Rdn
        if ( normName.equals( rdn.normName ) )
        {
            return true;
        }

        if ( rdn.nbAvas != nbAvas )
        {
            // We don't have the same number of Avas. The Rdn which
            // has the higher number of Ava is the one which is
            // superior
            return false;
        }

        switch ( nbAvas )
        {
            case 0:
                return true;

            case 1:
                return ava.equals( rdn.ava );

            default:
                // We have more than one value. We will
                // go through all of them.

                // the types are already normalized and sorted in the Avas Map
                // so we could compare the first element with all of the second
                // Ava elemnts, etc.
                Iterator<Ava> localIterator = avas.iterator();

                while ( localIterator.hasNext() )
                {
                    Iterator<Ava> paramIterator = rdn.avas.iterator();

                    Ava localAva = localIterator.next();
                    boolean equals = false;

                    while ( paramIterator.hasNext() )
                    {
                        Ava paramAva = paramIterator.next();

                        if ( localAva.equals( paramAva ) )
                        {
                            equals = true;
                            break;
                        }
                    }

                    if ( !equals )
                    {
                        return false;
                    }
                }

                return true;
        }
    }


    /**
     * Get the number of Avas of this Rdn
     *
     * @return The number of Avas in this Rdn
     */
    public int size()
    {
        return nbAvas;
    }


    /**
     * Unescape the given string according to RFC 2253 If in <string> form, a
     * LDAP string representation asserted value can be obtained by replacing
     * (left-to-right, non-recursively) each <pair> appearing in the <string> as
     * follows: replace <ESC><ESC> with <ESC>; replace <ESC><special> with
     * <special>; replace <ESC><hexpair> with the octet indicated by the
     * <hexpair> If in <hexstring> form, a BER representation can be obtained
     * from converting each <hexpair> of the <hexstring> to the octet indicated
     * by the <hexpair>
     *
     * @param value The value to be unescaped
     * @return Returns a string value as a String, and a binary value as a byte
     *         array.
     * @throws IllegalArgumentException When an Illegal value is provided.
     */
    public static Object unescapeValue( String value ) throws IllegalArgumentException
    {
        if ( Strings.isEmpty( value ) )
        {
            return "";
        }

        char[] chars = value.toCharArray();
        
        // If the value is contained into double quotes, return it as is.
        if ( ( chars[0] == '\"' ) && ( chars[chars.length - 1] == '\"' ) )
        {
            return value;
        }

        if ( chars[0] == '#' )
        {
            if ( chars.length == 1 )
            {
                // The value is only containing a #
                return StringConstants.EMPTY_BYTES;
            }

            if ( ( chars.length % 2 ) != 1 )
            {
                throw new IllegalArgumentException( I18n.err( I18n.ERR_04213 ) );
            }

            // HexString form
            byte[] hexValue = new byte[( chars.length - 1 ) / 2];
            int pos = 0;

            for ( int i = 1; i < chars.length; i += 2 )
            {
                if ( Chars.isHex( chars, i ) && Chars.isHex( chars, i + 1 ) )
                {
                    hexValue[pos++] = Hex.getHexValue( chars[i], chars[i + 1] );
                }
                else
                {
                    throw new IllegalArgumentException( I18n.err( I18n.ERR_04214 ) );
                }
            }

            return hexValue;
        }
        else
        {
            boolean escaped = false;
            boolean isHex = false;
            byte pair = -1;
            int pos = 0;

            byte[] bytes = new byte[chars.length * 6];

            for ( int i = 0; i < chars.length; i++ )
            {
                if ( escaped )
                {
                    escaped = false;

                    switch ( chars[i] )
                    {
                        case '\\':
                        case '"':
                        case '+':
                        case ',':
                        case ';':
                        case '<':
                        case '>':
                        case '#':
                        case '=':
                        case ' ':
                            bytes[pos++] = ( byte ) chars[i];
                            break;

                        default:
                            if ( Chars.isHex( chars, i ) )
                            {
                                isHex = true;
                                pair = ( ( byte ) ( Hex.getHexValue( chars[i] ) << 4 ) );
                            }

                            break;
                    }
                }
                else
                {
                    if ( isHex )
                    {
                        if ( Chars.isHex( chars, i ) )
                        {
                            pair += Hex.getHexValue( chars[i] );
                            bytes[pos++] = pair;
                            isHex = false;
                        }
                    }
                    else
                    {
                        switch ( chars[i] )
                        {
                            case '\\':
                                escaped = true;
                                break;

                            // We must not have a special char
                            // Specials are : '"', '+', ',', ';', '<', '>', ' ',
                            // '#' and '='
                            case '"':
                            case '+':
                            case ',':
                            case ';':
                            case '<':
                            case '>':
                            case '#':
                                if ( i != 0 )
                                {
                                    // '#' are allowed if not in first position
                                    bytes[pos++] = '#';
                                    break;
                                }
                            case '=':
                                throw new IllegalArgumentException( I18n.err( I18n.ERR_04215 ) );

                            case ' ':
                                if ( ( i == 0 ) || ( i == chars.length - 1 ) )
                                {
                                    throw new IllegalArgumentException( I18n.err( I18n.ERR_04215 ) );
                                }
                                else
                                {
                                    bytes[pos++] = ' ';
                                    break;
                                }

                            default:
                                if ( ( chars[i] >= 0 ) && ( chars[i] < 128 ) )
                                {
                                    bytes[pos++] = ( byte ) chars[i];
                                }
                                else
                                {
                                    byte[] result = Unicode.charToBytes( chars[i] );
                                    System.arraycopy( result, 0, bytes, pos, result.length );
                                    pos += result.length;
                                }

                                break;
                        }
                    }
                }
            }

            return Strings.utf8ToString( bytes, pos );
        }
    }


    /**
     * Transform a value in a String, accordingly to RFC 2253
     *
     * @param value The attribute value to be escaped
     * @return The escaped string value.
     */
    public static String escapeValue( String value )
    {
        if ( Strings.isEmpty( value ) )
        {
            return "";
        }

        byte[] bytes = Strings.getBytesUtf8( value );
        byte[] newBytes = new byte[bytes.length * 3];
        int pos = 0;

        for ( int i = 0; i < bytes.length; i++ )
        {
            if ( ( bytes[i] & 0x0080 ) != 0 )
            {
                newBytes[pos++] = '\\';
                newBytes[pos++] = (byte)Strings.dumpHex( (byte)( (bytes[i] & 0x00F0 )  >> 4 ) );
                newBytes[pos++] = (byte)Strings.dumpHex( (byte)(bytes[i] & 0x000F) );
            }
            else
            {
                switch ( bytes[i] )
                {
                    case ' ':
                        if ( ( i > 0 ) && ( i < bytes.length - 1 ) )
                        {
                            newBytes[pos++] = bytes[i];
                        }
                        else
                        {
                            newBytes[pos++] = '\\';
                            newBytes[pos++] = bytes[i];
                        }
    
                        break;
    
                    case '#':
                        if ( i != 0 )
                        {
                            newBytes[pos++] = bytes[i];
                        }
                        else
                        {
                            newBytes[pos++] = '\\';
                            newBytes[pos++] = bytes[i];
                        }
    
                        break;
    
                    case '"':
                    case '+':
                    case ',':
                    case ';':
                    case '=':
                    case '<':
                    case '>':
                    case '\\':
                        newBytes[pos++] = '\\';
                        newBytes[pos++] = bytes[i];
                        break;
    
                    case 0x7F:
                        newBytes[pos++] = '\\';
                        newBytes[pos++] = '7';
                        newBytes[pos++] = 'F';
                        break;
    
                    case 0x00:
                    case 0x01:
                    case 0x02:
                    case 0x03:
                    case 0x04:
                    case 0x05:
                    case 0x06:
                    case 0x07:
                    case 0x08:
                    case 0x09:
                    case 0x0A:
                    case 0x0B:
                    case 0x0C:
                    case 0x0D:
                    case 0x0E:
                    case 0x0F:
                        newBytes[pos++] = '\\';
                        newBytes[pos++] = '0';
                        newBytes[pos++] = (byte)Strings.dumpHex( ( byte ) ( bytes[i] & 0x0F ) );
                        break;
    
                    case 0x10:
                    case 0x11:
                    case 0x12:
                    case 0x13:
                    case 0x14:
                    case 0x15:
                    case 0x16:
                    case 0x17:
                    case 0x18:
                    case 0x19:
                    case 0x1A:
                    case 0x1B:
                    case 0x1C:
                    case 0x1D:
                    case 0x1E:
                    case 0x1F:
                        newBytes[pos++] = '\\';
                        newBytes[pos++] = '1';
                        newBytes[pos++] = (byte)Strings.dumpHex( ( byte ) ( bytes[i] & 0x0F ) );
                        break;
    
                    default:
                        newBytes[pos++] = bytes[i];
                        break;
                }
            }
        }

        return new String( newBytes, 0, pos );
    }


    /**
     * Transform a value in a String, accordingly to RFC 2253
     *
     * @param attrValue
     *            The attribute value to be escaped
     * @return The escaped string value.
     */
    public static String escapeValue( byte[] attrValue )
    {
        if ( Strings.isEmpty( attrValue ) )
        {
            return "";
        }

        String value = Strings.utf8ToString( attrValue );

        return escapeValue( value );
    }


    /**
     * Tells if the Rdn is schema aware.
     *
     * @return <code>true</code> if the Rdn is schema aware
     */
    public boolean isSchemaAware()
    {
        return schemaManager != null;
    }


    /**
     * Validate a NameComponent : <br>
     * <p>
     * &lt;name-component&gt; ::= &lt;attributeType&gt; &lt;spaces&gt; '='
     * &lt;spaces&gt; &lt;attributeValue&gt; &lt;nameComponents&gt;
     * </p>
     *
     * @param dn The string to parse
     * @return <code>true</code> if the Rdn is valid
     */
    public static boolean isValid( String dn )
    {
        Rdn rdn = new Rdn();
        try
        {
            parse( dn, rdn );
            return true;
        }
        catch ( LdapInvalidDnException e )
        {
            return false;
        }
    }


    /**
     * Parse a NameComponent : <br>
     * <p>
     * &lt;name-component&gt; ::= &lt;attributeType&gt; &lt;spaces&gt; '='
     * &lt;spaces&gt; &lt;attributeValue&gt; &lt;nameComponents&gt;
     * </p>
     *
     * @param dn The String to parse
     * @param rdn The Rdn to fill. Beware that if the Rdn is not empty, the new
     *            AttributeTypeAndValue will be added.
     * @throws LdapInvalidDnException If the NameComponent is invalid
     */
    private static void parse( String dn, Rdn rdn ) throws LdapInvalidDnException
    {
        try
        {
            FastDnParser.parseRdn( dn, rdn );
        }
        catch ( TooComplexException e )
        {
            rdn.clear();
            new ComplexDnParser().parseRdn( dn, rdn );
        }
    }


    /**
      * Gets the hashcode of this rdn.
      *
      * @see java.lang.Object#hashCode()
      * @return the instance's hash code
      */
    public int hashCode()
    {
        if ( h == 0 )
        {
            h = 37;

            switch ( nbAvas )
            {
                case 0:
                    // An empty Rdn
                    break;

                case 1:
                    // We have a single Ava
                    h = h * 17 + ava.hashCode();
                    break;

                default:
                    // We have more than one Ava

                    for ( Ava ata : avas )
                    {
                        h = h * 17 + ata.hashCode();
                    }

                    break;
            }
        }

        return h;
    }


    /**
     * A Rdn is composed of on to many Avas (AttributeType And Value).
     * We should write all those Avas sequencially, following the
     * structure :
     * <ul>
     *   <li>
     *     <b>parentId</b> The parent entry's Id
     *   </li>
     *   <li>
     *     <b>nbAvas</b> The number of Avas to write. Can't be 0.
     *   </li>
     *   <li>
     *     <b>upName</b> The User provided Rdn
     *   </li>
     *   <li>
     *     <b>normName</b> The normalized Rdn. It can be empty if the normalized
     * name equals the upName.
     *   </li>
     *   <li>
     *     <b>Avas</b>
     *   </li>
     * </ul>
     * <br/>
     * For each Ava :
     * <ul>
     *   <li>
     *     <b>start</b> The position of this Ava in the upName string
     *   </li>
     *   <li>
     *     <b>length</b> The Ava user provided length
     *   </li>
     *   <li>
     *     <b>Call the Ava write method</b> The Ava itself
     *   </li>
     * </ul>
     *
     * @see Externalizable#readExternal(ObjectInput)
     * @param out The stream into which the serialized Rdn will be put
     * @throws IOException If the stream can't be written
     */
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        out.writeInt( nbAvas );
        out.writeUTF( upName );

        if ( upName.equals( normName ) )
        {
            out.writeUTF( "" );
        }
        else
        {
            out.writeUTF( normName );
        }

        switch ( nbAvas )
        {
            case 0:
                break;

            case 1:
                ava.writeExternal( out );
                break;

            default:
                for ( Ava localAva : avas )
                {
                    localAva.writeExternal( out );
                }

                break;
        }

        out.writeInt( h );

        out.flush();
    }


    /**
     * We read back the data to create a new RDB. The structure
     * read is exposed in the {@link Rdn#writeExternal(ObjectOutput)}
     * method
     *
     * @see Externalizable#readExternal(ObjectInput)
     * @param in The input stream from which the Rdn will be read
     * @throws IOException If we can't read from the input stream
     * @throws ClassNotFoundException If we can't create a new Rdn
     */
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException
    {
        // Read the Ava number
        nbAvas = in.readInt();

        // Read the UPName
        upName = in.readUTF();

        // Read the normName
        normName = in.readUTF();

        if ( Strings.isEmpty( normName ) )
        {
            normName = upName;
        }

        switch ( nbAvas )
        {
            case 0:
                ava = null;
                break;

            case 1:
                ava = new Ava( schemaManager );
                ava.readExternal( in );
                avaType = ava.getNormType();

                break;

            default:
                avas = new ArrayList<Ava>();

                avaTypes = new MultiValueMap();

                for ( int i = 0; i < nbAvas; i++ )
                {
                    Ava ava = new Ava( schemaManager );
                    ava.readExternal( in );
                    avas.add( ava );
                    avaTypes.put( ava.getNormType(), ava );
                }

                ava = null;
                avaType = null;

                break;
        }

        h = in.readInt();
    }


    public int compareTo( Rdn arg0 )
    {
        // TODO Auto-generated method stub
        return 0;
    }


    /**
     * @return a String representation of the Rdn. The caller will get back the user
     * provided Rdn
     */
    public String toString()
    {
        return upName == null ? "" : upName;
    }
}
