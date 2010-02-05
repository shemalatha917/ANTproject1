/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.apache.tools.ant.taskdefs.optional.junit;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Parser of XML-output generated by the JUnit XML formatter.
 *
 * @author Marian Petras
 */
final class XMLResultParser extends DefaultHandler {

    /** */
    private static final int STATE_OUT_OF_SCOPE = 1;
    /** */
    private static final int STATE_TESTSUITE = 2;
    /** */
    private static final int STATE_PROPERTIES = 3;
    /** */
    private static final int STATE_PROPERTY = 4;
    /** */
    private static final int STATE_TESTCASE = 8;
    /** */
    private static final int STATE_FAILURE = 12;
    /** */
    private static final int STATE_ERROR = 13;
    /** */
    private static final int STATE_OUTPUT_STD = 16;
    /** */
    private static final int STATE_OUTPUT_ERR = 17;
    
    /** */
    private int state = STATE_OUT_OF_SCOPE;
    /** */
    int unknownElemNestLevel = 0;
    
    /** */
    private final XMLReader xmlReader;
    /** */
    private String lastErrClassName = null;
    /** */
    private List lastErrMethodNames = null;
    /** */
    private String className;
    /** */
    private String methodName;

    /** */
    private List result = null;
    
    /**
     * Parses an XML file provided by a given reader.
     * @param reader reader to read the XML file from
     * @return list of Strings, each describing a single <code>JUnitTest</code>
     * @exception java.io.IOException
     *            in case of troubles with reading the file
     * @exception org.xml.sax.SAXException
     *            if initialization of the parser failed
     */
    static List parseResultsFile(Reader reader) throws IOException, SAXException {
        XMLResultParser parser = new XMLResultParser();
        parser.xmlReader.parse(new InputSource(reader));
        return (parser.result != null) ? parser.result
                                       : Collections.EMPTY_LIST;
    }
    
    /** Creates a new instance of XMLResultParser */
    private XMLResultParser() throws SAXException {
        xmlReader = XMLReaderFactory.createXMLReader();
        xmlReader.setContentHandler(this);
    }
    
    /**
     */
    public void startElement(String uri,
                             String localName,
                             String qName,
                             Attributes attrs) throws SAXException {
        switch (state) {
            case STATE_PROPERTIES:
                if (qName.equals("property")) {
                    state = STATE_PROPERTY;
                } else {
                    startUnknownElem();
                }
                break;
            case STATE_TESTSUITE:
                if (qName.equals("testcase")) {
                    className = attrs.getValue("classname");
                    methodName = attrs.getValue("name");
                    state = STATE_TESTCASE;
                } else if (qName.equals("system-out")) {
                    state = STATE_OUTPUT_STD;
                } else if (qName.equals("system-err")) {
                    state = STATE_OUTPUT_ERR;
                } else if (qName.equals("properties")) {
                    state = STATE_PROPERTIES;
                } else {
                    startUnknownElem();
                }
                break;
            case STATE_TESTCASE:
                if (qName.equals("failure")) {
                    state = STATE_FAILURE;
                } else if (qName.equals("error")) {
                    state = STATE_ERROR;
                } else {
                    startUnknownElem();
                }
                if (state >= 0) {     //i.e. the element is "failure" or "error"
                    if ((className != null) && (methodName != null)) {
                        if (className.equals(lastErrClassName)) {
                            lastErrMethodNames.add(methodName);
                        } else {
                            maybeSaveLastErrJUnitTest();
                        }
                        
                        lastErrClassName = className;
                        if (lastErrMethodNames == null) {
                            lastErrMethodNames = new ArrayList(5);
                        }
                        lastErrMethodNames.add(methodName);
                    }
                }
                break;
            case STATE_OUT_OF_SCOPE:
                if (qName.equals("testsuite")) {
                    state = STATE_TESTSUITE;
                } else {
                    startUnknownElem();
                }
                break;
            case STATE_PROPERTY:
            case STATE_FAILURE:
            case STATE_ERROR:
            case STATE_OUTPUT_STD:
            case STATE_OUTPUT_ERR:
                startUnknownElem();
                break;
            default:
                unknownElemNestLevel++;
                break;
        }
    }

    /**
     */
    public void endElement(String uri,
                           String localName,
                           String qName) throws SAXException {
        switch (state) {
            case STATE_PROPERTIES:
                state = STATE_TESTSUITE;
                break;
            case STATE_TESTSUITE:
                maybeSaveLastErrJUnitTest();
                state = STATE_OUT_OF_SCOPE;
                break;
            case STATE_TESTCASE:
                state = STATE_TESTSUITE;
                break;
            case STATE_OUT_OF_SCOPE:
                break;
            case STATE_PROPERTY:
                state = STATE_PROPERTIES;
                break;
            case STATE_FAILURE:
            case STATE_ERROR:
                state = STATE_TESTCASE;
                break;
            case STATE_OUTPUT_STD:
            case STATE_OUTPUT_ERR:
                state = STATE_TESTSUITE;
                break;
            default:
                if (--unknownElemNestLevel == 0) {
                    state = -state;
                }
                break;
        }
    }
    
    /**
     */
    private void startUnknownElem() {
        state = -state;
        unknownElemNestLevel++;
    }
    
    /**
     */
    private void maybeSaveLastErrJUnitTest() {
        if (lastErrClassName == null) {
            return;
        }

        StringBuffer buf = new StringBuffer(20);
        buf.append(lastErrClassName).append(':');
        buf.append(lastErrMethodNames.get(0));
        int methodsCount = lastErrMethodNames.size();
        if (methodsCount > 1) {
            for (int i = 1; i < methodsCount; i++) {
                buf.append(',').append(lastErrMethodNames.get(i));
            }
        }
        if (result == null) {
            result = new ArrayList(10);
        }
        result.add(buf.toString());

        lastErrClassName = null;
        lastErrMethodNames.clear();
    }
    
}
