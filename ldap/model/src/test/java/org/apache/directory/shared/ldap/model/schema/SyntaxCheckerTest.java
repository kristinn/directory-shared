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
package org.apache.directory.shared.ldap.model.schema;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.mycila.junit.concurrent.Concurrency;
import com.mycila.junit.concurrent.ConcurrentJunitRunner;
import org.apache.directory.shared.ldap.model.schema.syntaxCheckers.AccessPointSyntaxChecker;
import org.apache.directory.shared.ldap.model.schema.syntaxCheckers.CountrySyntaxChecker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests class SyntaxChecker.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith(ConcurrentJunitRunner.class)
@Concurrency()
public class SyntaxCheckerTest
{
    AbstractSyntaxChecker objectClassA;
    AbstractSyntaxChecker objectClassACopy;
    AbstractSyntaxChecker objectClassB;
    AbstractSyntaxChecker objectClassC;


    /**
     * Initialize name instances
     */
    @Before
    public void initNames() throws Exception
    {
        objectClassA = new AccessPointSyntaxChecker();
        objectClassACopy = new AccessPointSyntaxChecker();
        objectClassB = new AccessPointSyntaxChecker();
        objectClassC = new CountrySyntaxChecker();
    }


    @Test
    public void testEqualsNull() throws Exception
    {
        assertFalse( objectClassA.equals( null ) );
    }


    @Test
    public void testEqualsReflexive() throws Exception
    {
        assertEquals( objectClassA, objectClassA );
    }


    @Test
    public void testHashCodeReflexive() throws Exception
    {
        assertEquals( objectClassA.hashCode(), objectClassA.hashCode() );
    }


    @Test
    public void testEqualsSymmetric() throws Exception
    {
        assertEquals( objectClassA, objectClassACopy );
        assertEquals( objectClassACopy, objectClassA );
    }


    @Test
    public void testHashCodeSymmetric() throws Exception
    {
        assertEquals( objectClassA.hashCode(), objectClassACopy.hashCode() );
        assertEquals( objectClassACopy.hashCode(), objectClassA.hashCode() );
    }


    @Test
    public void testEqualsTransitive() throws Exception
    {
        assertEquals( objectClassA, objectClassACopy );
        assertEquals( objectClassACopy, objectClassB );
        assertEquals( objectClassA, objectClassB );
    }


    @Test
    public void testHashCodeTransitive() throws Exception
    {
        assertEquals( objectClassA.hashCode(), objectClassACopy.hashCode() );
        assertEquals( objectClassACopy.hashCode(), objectClassB.hashCode() );
        assertEquals( objectClassA.hashCode(), objectClassB.hashCode() );
    }


    @Test
    public void testNotEqualDiffValue() throws Exception
    {
        assertFalse( objectClassA.equals( objectClassC ) );
        assertFalse( objectClassC.equals( objectClassA ) );
    }
}
