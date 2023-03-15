package se.laz.casual.inbound.discover;

import se.laz.casual.api.network.protocol.messages.CasualNWMessage;
import se.laz.casual.config.ConfigurationService;
import se.laz.casual.config.Domain;
import se.laz.casual.internal.network.NetworkConnection;
import se.laz.casual.network.ProtocolVersion;
import se.laz.casual.network.outbound.NettyConnectionInformation;
import se.laz.casual.network.outbound.NettyConnectionInformationCreator;
import se.laz.casual.network.outbound.NettyNetworkConnection;
import se.laz.casual.network.outbound.NetworkListener;
import se.laz.casual.network.protocol.messages.CasualNWMessageImpl;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryReplyMessage;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryRequestMessage;
import se.laz.casual.network.protocol.messages.domain.Service;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DomainDiscover implements NetworkListener
{
    private static final Logger LOG = Logger.getLogger(DomainDiscover.class.getName());
    private final String host;
    private int port;
    private final ProtocolVersion protocolVersion;

    private DomainDiscover(String host, int port, ProtocolVersion protocolVersion)
    {
        this.host = host;
        this.port = port;
        this.protocolVersion = protocolVersion;
    }

    public static DomainDiscover of(String host, int port, ProtocolVersion protocolVersion)
    {
        Objects.requireNonNull(host, "host can not be null");
        if(port < 1)
        {
            throw new IllegalArgumentException("Port is " + port + " which is not greater than 0");
        }
        return new DomainDiscover(host, port, protocolVersion);
    }

    /**
     * As long as this does not throw we're ok
     * @return
     */
    public void issueDiscovery()
    {
        LOG.info(() -> "configuration: " + ConfigurationService.getInstance().getConfiguration());
        NetworkConnection newNetworkConnection = null;
        try
        {
            newNetworkConnection = createNetworkConnection();
            serviceExists(UUID.randomUUID(), "discoveryRequest", newNetworkConnection);
        }
        finally
        {
            if(null != newNetworkConnection)
            {
                newNetworkConnection.close();
            }
        }
    }

    private NetworkConnection createNetworkConnection()
    {
        NettyConnectionInformation ci = NettyConnectionInformationCreator.create(new InetSocketAddress(host, port), protocolVersion);
        return NettyNetworkConnection.of(ci, this);
    }

    private boolean serviceExists(UUID corrid, String serviceName, NetworkConnection newNetworkConnection)
    {
        CasualNWMessage<CasualDomainDiscoveryReplyMessage> replyMsg = serviceDiscovery(corrid, serviceName, newNetworkConnection);
        LOG.info(() -> "replyMsg: " + replyMsg);
        return replyMsg.getMessage().getServices().stream()
                       .map(Service::getName)
                       .anyMatch(v -> v.equals(serviceName));
    }

    private CasualNWMessage<CasualDomainDiscoveryReplyMessage> serviceDiscovery(UUID corrid, String serviceName, NetworkConnection newNetworkConnection)
    {
        Domain domain = ConfigurationService.getInstance().getConfiguration().getDomain();
        CasualDomainDiscoveryRequestMessage requestMsg = CasualDomainDiscoveryRequestMessage.createBuilder()
                                                                                            .setExecution(UUID.randomUUID())
                                                                                            .setDomainId(domain.getId())
                                                                                            .setDomainName(domain.getName())
                                                                                            .setServiceNames(Arrays.asList(serviceName))
                                                                                            .build();
        CasualNWMessage<CasualDomainDiscoveryRequestMessage> msg = CasualNWMessageImpl.of(corrid, requestMsg);
        CompletableFuture<CasualNWMessage<CasualDomainDiscoveryReplyMessage>> replyMsgFuture = newNetworkConnection.request(msg);

        CasualNWMessage<CasualDomainDiscoveryReplyMessage> replyMsg = replyMsgFuture.join();
        return replyMsg;
    }

    @Override
    public void disconnected(Exception reason)
    {
        // NOP
    }

    public static void main(String[] args)
    {
        if(args.length != 2)
        {
            System.out.println("Usage: java -jar domain-discovery.jar hostname port-number");
            System.exit(-1);
        }
        try
        {
            int port = Integer.parseInt(args[1]);
            // As long as domain discovery does not throw, we're good
            DomainDiscover domainDiscover = DomainDiscover.of(args[0], port, ProtocolVersion.VERSION_1_0);
            domainDiscover.issueDiscovery();
            System.exit(0);
        }
        catch(Exception e)
        {
            LOG.log(Level.SEVERE, e, () -> "inbound domain discovery failed");
            System.exit(-1);
        }
    }

}
