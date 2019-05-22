package org.jboss.resteasy.client.jaxrs.internal;

/**
 * mstodo: Header
 *
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 22/05/2019
 */
public interface CleanableRxInvoker {

    void setCleanUp(Runnable asyncInvocationCleanUp);
}
