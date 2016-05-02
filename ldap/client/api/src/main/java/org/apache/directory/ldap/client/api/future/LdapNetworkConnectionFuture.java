package org.apache.directory.ldap.client.api.future;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;

/**
 * Data class containing future LDAP connection results.
 */
public class LdapNetworkConnectionFuture
{
    private IoConnector connector;
    private ConnectFuture connectFuture;

    public ConnectFuture getConnectFuture()
    {
        return connectFuture;
    }

    public void setConnectFuture( ConnectFuture connectFuture )
    {
        this.connectFuture = connectFuture;
    }

    public IoConnector getConnector()
    {
        return connector;
    }

    public void setConnector( IoConnector connector )
    {
        this.connector = connector;
    }
}
