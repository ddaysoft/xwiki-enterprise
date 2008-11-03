package com.xpn.xwiki.it;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jmock.Mock;
import org.jmock.core.Invocation;
import org.jmock.core.stub.CustomStub;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheFactory;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.internal.DefaultCache;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.it.framework.AbstractLDAPTestCase;
import com.xpn.xwiki.it.framework.LDAPTestSetup;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.store.XWikiStoreInterface;
import com.xpn.xwiki.user.impl.LDAP.LDAPProfileXClass;
import com.xpn.xwiki.user.impl.LDAP.XWikiLDAPAuthServiceImpl;

/**
 * Unit tests using embedded LDAP server (Apache DS). Theses test can be launched directly from JUnit plugin of EDI.
 * 
 * @version $Id$
 */
public class XWikiLDAPAuthServiceImplTest extends AbstractLDAPTestCase
{
    private static final String MAIN_WIKI_NAME = "xwiki";

    private XWikiLDAPAuthServiceImpl ldapAuth = new XWikiLDAPAuthServiceImpl();

    private CacheFactory cacheFactory = new CacheFactory()
    {
        public <T> Cache<T> newCache(CacheConfiguration config) throws CacheException
        {
            return new DefaultCache<T>();
        }
    };

    private Properties properties = new Properties();

    private boolean isVirtualMode = false;

    private Map<String, Map<String, XWikiDocument>> databases = new HashMap<String, Map<String, XWikiDocument>>();

    private BaseClass userClass = new BaseClass();

    private Mock mockStore;

    private Map<String, XWikiDocument> getDocuments(String database, boolean create) throws XWikiException
    {
        if (database == null) {
            database = getContext().getDatabase();
        }

        if (database == null || database.length() == 0) {
            database = MAIN_WIKI_NAME;
        }

        if (!this.databases.containsKey(database)) {
            if (create) {
                this.databases.put(database, new HashMap<String, XWikiDocument>());
            } else {
                throw new XWikiException(XWikiException.MODULE_XWIKI_STORE, XWikiException.ERROR_XWIKI_UNKNOWN,
                    "Database " + database + " does not exists.");
            }
        }

        return this.databases.get(database);
    }

    private XWikiDocument getDocument(String documentFullName) throws XWikiException
    {
        XWikiDocument document = new XWikiDocument();
        document.setFullName(documentFullName);

        Map<String, XWikiDocument> docs = getDocuments(document.getDatabase(), false);

        if (docs.containsKey(document.getFullName())) {
            return docs.get(document.getFullName());
        } else {
            return document;
        }
    }

    private void saveDocument(XWikiDocument document) throws XWikiException
    {
        document.setNew(false);
        getDocuments(document.getDatabase(), true).put(document.getFullName(), document);
    }

    private boolean documentExists(String documentFullName) throws XWikiException
    {
        return !getDocument(documentFullName).isNew();
    }

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        getContext().setDatabase(MAIN_WIKI_NAME);
        getContext().setMainXWiki(MAIN_WIKI_NAME);

        this.databases.put(MAIN_WIKI_NAME, new HashMap<String, XWikiDocument>());

        this.mockStore = mock(XWikiStoreInterface.class, new Class[] {}, new Object[] {});

        this.mockStore.stubs().method("searchDocuments").will(returnValue(Collections.EMPTY_LIST));

        Mock mockXWiki = mock(XWiki.class, new Class[] {}, new Object[] {});

        mockXWiki.stubs().method("getStore").will(returnValue(mockStore.proxy()));
        mockXWiki.stubs().method("getCacheFactory").will(returnValue(this.cacheFactory));
        mockXWiki.stubs().method("getXWikiPreference").will(returnValue(null));
        mockXWiki.stubs().method("getXWikiPreferenceAsInt").will(throwException(new NumberFormatException("null")));
        mockXWiki.stubs().method("isVirtualMode").will(returnValue(this.isVirtualMode));
        mockXWiki.stubs().method("Param").will(new CustomStub("Implements XWiki.Param")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                return properties.getProperty((String) invocation.parameterValues.get(0));
            }
        });
        mockXWiki.stubs().method("ParamAsLong").will(new CustomStub("Implements XWiki.ParamAsLong")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                return Long.parseLong(properties.getProperty((String) invocation.parameterValues.get(0)));
            }
        });
        mockXWiki.stubs().method("getDocument").will(new CustomStub("Implements XWiki.getDocument")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                return getDocument((String) invocation.parameterValues.get(0));
            }
        });
        mockXWiki.stubs().method("saveDocument").will(new CustomStub("Implements XWiki.saveDocument")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                saveDocument((XWikiDocument) invocation.parameterValues.get(0));

                return null;
            }
        });
        mockXWiki.stubs().method("exists").will(new CustomStub("Implements XWiki.exists")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                return documentExists((String) invocation.parameterValues.get(0));
            }
        });
        mockXWiki.stubs().method("getClass").will(new CustomStub("Implements XWiki.getClass")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                return getDocument((String) invocation.parameterValues.get(0)).getxWikiClass();
            }
        });
        mockXWiki.stubs().method("search").will(returnValue(Collections.EMPTY_LIST));

        this.userClass.setName("XWiki.XWikiUsers");

        this.userClass.addTextField("first_name", "First Name", 30);
        this.userClass.addTextField("last_name", "Last Name", 30);
        this.userClass.addTextField("email", "e-Mail", 30);
        this.userClass.addPasswordField("password", "Password", 10);

        mockXWiki.stubs().method("getUserClass").will(returnValue(this.userClass));

        mockXWiki.stubs().method("createUser").will(new CustomStub("Implements XWiki.createUser")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                XWikiDocument document = new XWikiDocument();
                document.setFullName("XWiki." + invocation.parameterValues.get(0));

                BaseObject newobject = new BaseObject();
                newobject.setClassName(userClass.getName());
                document.addObject(userClass.getName(), newobject);

                saveDocument(document);

                return 1;
            }
        });

        getContext().setWiki((XWiki) mockXWiki.proxy());

        this.properties.setProperty("xwiki.authentication.ldap", "1");
        this.properties.setProperty("xwiki.authentication.ldap.server", LDAPTestSetup.LDAP_SERVER);
        this.properties.setProperty("xwiki.authentication.ldap.port", "" + LDAPTestSetup.getLDAPPort());
        this.properties.setProperty("xwiki.authentication.ldap.base_DN", LDAPTestSetup.LDAP_BASEDN);
        this.properties.setProperty("xwiki.authentication.ldap.bind_DN", LDAPTestSetup.LDAP_BINDDN_CN);
        this.properties.setProperty("xwiki.authentication.ldap.bind_pass", LDAPTestSetup.LDAP_BINDPASS_CN);
        this.properties.setProperty("xwiki.authentication.ldap.UID_attr", LDAPTestSetup.LDAP_USERUID_FIELD);
        this.properties.setProperty("xwiki.authentication.ldap.groupcache_expiration", "1");
        this.properties.setProperty("xwiki.authentication.ldap.try_local", "0");
        this.properties.setProperty("xwiki.authentication.ldap.update_user", "1");
        this.properties.setProperty("xwiki.authentication.ldap.fields_mapping",
            "last_name=sn,first_name=givenName,fullname=fullName,email=mail");
    }

    private void testAuthenticate(String login, String password, String storedDn) throws XWikiException
    {
        testAuthenticate(login, password, login, storedDn);
    }

    private void testAuthenticate(String login, String password, String validUserName, String storedDn)
        throws XWikiException
    {
        testAuthenticate(login, password, validUserName, storedDn, login);
    }

    private void testAuthenticate(String login, String password, String validUserName, String storedDn, String storedUid)
        throws XWikiException
    {
        Principal principal = this.ldapAuth.authenticate(login, password, getContext());

        // Check that authentication return a valid Principal
        assertNotNull(principal);

        // Check that the returned Principal has the good name
        assertEquals("xwiki:XWiki." + validUserName, principal.getName());

        XWikiDocument userProfile = getDocument("XWiki." + validUserName);

        // check hat user has been created
        assertTrue(!userProfile.isNew());

        BaseObject ldapProfile = userProfile.getObject(LDAPProfileXClass.LDAP_XCLASS);

        assertNotNull(ldapProfile);

        assertEquals(storedDn, ldapProfile.getStringValue(LDAPProfileXClass.LDAP_XFIELD_DN));
        assertEquals(storedUid, ldapProfile.getStringValue(LDAPProfileXClass.LDAP_XFIELD_UID));
    }

    /**
     * Validate "simple" LDAP authentication.
     */
    public void testAuthenticate() throws XWikiException
    {
        testAuthenticate(LDAPTestSetup.HORATIOHORNBLOWER_CN, LDAPTestSetup.HORATIOHORNBLOWER_PWD,
            LDAPTestSetup.HORATIOHORNBLOWER_DN);
    }

    /**
     * Validate the same user profile is used when authentication is called twice for same user.
     */
    public void testAuthenticateTwice() throws XWikiException
    {
        testAuthenticate(LDAPTestSetup.HORATIOHORNBLOWER_CN, LDAPTestSetup.HORATIOHORNBLOWER_PWD,
            LDAPTestSetup.HORATIOHORNBLOWER_DN);

        this.mockStore.stubs().method("searchDocuments").will(
            returnValue(Collections.singletonList(getDocument("XWiki." + LDAPTestSetup.HORATIOHORNBLOWER_CN))));

        testAuthenticate(LDAPTestSetup.HORATIOHORNBLOWER_CN, LDAPTestSetup.HORATIOHORNBLOWER_PWD,
            LDAPTestSetup.HORATIOHORNBLOWER_DN);
    }

    /**
     * Validate the same user profile is used when authentication is called twice for same user even the uid used have
     * different case.
     */
    public void testAuthenticateTwiceAndDifferentCase() throws XWikiException
    {
        testAuthenticate(LDAPTestSetup.HORATIOHORNBLOWER_CN, LDAPTestSetup.HORATIOHORNBLOWER_PWD,
            LDAPTestSetup.HORATIOHORNBLOWER_DN);

        this.mockStore.stubs().method("searchDocuments").will(
            returnValue(Collections.singletonList(getDocument("XWiki." + LDAPTestSetup.HORATIOHORNBLOWER_CN))));

        testAuthenticate(LDAPTestSetup.HORATIOHORNBLOWER_CN.toUpperCase(), LDAPTestSetup.HORATIOHORNBLOWER_PWD,
            LDAPTestSetup.HORATIOHORNBLOWER_CN, LDAPTestSetup.HORATIOHORNBLOWER_DN, LDAPTestSetup.HORATIOHORNBLOWER_CN);
    }

    /**
     * Validate "simple" LDAP authentication when uid contains point(s).
     */
    public void testAuthenticateWhenUidContainsPoints() throws XWikiException
    {
        testAuthenticate(LDAPTestSetup.USERWITHPOINTS_CN, LDAPTestSetup.USERWITHPOINTS_PWD,
            LDAPTestSetup.USERWITHPOINTS_CN.replaceAll("\\.", ""), LDAPTestSetup.USERWITHPOINTS_DN);
    }

    /**
     * Validate a different profile is used for different uid containing points but having same cleaned uid.
     */
    public void testAuthenticateTwiceWhenDifferentUsersAndUidContainsPoints() throws XWikiException
    {
        testAuthenticate(LDAPTestSetup.USERWITHPOINTS_CN, LDAPTestSetup.USERWITHPOINTS_PWD,
            LDAPTestSetup.USERWITHPOINTS_CN.replaceAll("\\.", ""), LDAPTestSetup.USERWITHPOINTS_DN);

        testAuthenticate(LDAPTestSetup.OTHERUSERWITHPOINTS_CN, LDAPTestSetup.OTHERUSERWITHPOINTS_PWD,
            LDAPTestSetup.OTHERUSERWITHPOINTS_CN.replaceAll("\\.", "") + "_1", LDAPTestSetup.OTHERUSERWITHPOINTS_DN);
    }

    /**
     * Validate "simple" LDAP authentication when the user already exists but does not contains LDAP profile object.
     */
    public void testAuthenticateWhenNonLDAPUserAlreadyExists() throws XWikiException
    {
        XWikiDocument userDoc = getDocument("XWiki." + LDAPTestSetup.HORATIOHORNBLOWER_CN);
        userDoc.newObject(this.userClass.getName(), getContext());
        saveDocument(userDoc);

        testAuthenticate(LDAPTestSetup.HORATIOHORNBLOWER_CN, LDAPTestSetup.HORATIOHORNBLOWER_PWD,
            LDAPTestSetup.HORATIOHORNBLOWER_DN);
    }

    /**
     * Validate "simple" LDAP authentication when the user profile default page already exists but does not contains
     * user object. In that case it is using another document to create the user.
     */
    public void testAuthenticateWhenNonLDAPNonUserAlreadyExists() throws XWikiException
    {
        XWikiDocument userDoc = getDocument("XWiki." + LDAPTestSetup.HORATIOHORNBLOWER_CN);
        saveDocument(userDoc);

        testAuthenticate(LDAPTestSetup.HORATIOHORNBLOWER_CN, LDAPTestSetup.HORATIOHORNBLOWER_PWD,
            LDAPTestSetup.HORATIOHORNBLOWER_CN + "_1", LDAPTestSetup.HORATIOHORNBLOWER_DN);
    }
}
