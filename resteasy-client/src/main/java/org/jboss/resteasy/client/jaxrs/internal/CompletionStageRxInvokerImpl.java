package org.jboss.resteasy.client.jaxrs.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.CompletionStageRxInvoker;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.SyncInvoker;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

/**
 *
 * @author <a href="mailto:ron.sigal@jboss.com">Ron Sigal</a>
 * @author <a href="mailto:alessio.soldano@jboss.com">Alessio Soldano</a>
 * <p>
 * Date March 9, 2016
 */
public class CompletionStageRxInvokerImpl implements CompletionStageRxInvoker, CleanableRxInvoker
{
   private final SyncInvoker builder;

   private final ExecutorService executor;

   private Runnable cleanUp;

   public CompletionStageRxInvokerImpl(final SyncInvoker builder)
   {
      this(builder, null);
   }

   public CompletionStageRxInvokerImpl(final SyncInvoker builder, final ExecutorService executor)
   {
      Thread.dumpStack();
      this.builder = builder;
      this.executor = executor;
   }

   @Override
   public CompletionStage<Response> get()
   {
      return supplyAsync(() -> builder.get());
   }

   @Override
   public <T> CompletionStage<T> get(Class<T> responseType)
   {
      return supplyAsync(() -> builder.get(responseType));
   }

   @Override
   public <T> CompletionStage<T> get(GenericType<T> responseType)
   {
      return supplyAsync(() -> builder.get(responseType));
   }

   @Override
   public CompletionStage<Response> put(Entity<?> entity)
   {
      return supplyAsync(() -> builder.put(entity));
   }

   @Override
   public <T> CompletionStage<T> put(Entity<?> entity, Class<T> clazz)
   {
      return supplyAsync(() -> builder.put(entity, clazz));
   }

   @Override
   public <T> CompletionStage<T> put(Entity<?> entity, GenericType<T> type)
   {
      return supplyAsync(() -> builder.put(entity, type));
   }

   @Override
   public CompletionStage<Response> post(Entity<?> entity)
   {
      return supplyAsync(() -> builder.post(entity));
   }

   @Override
   public <T> CompletionStage<T> post(Entity<?> entity, Class<T> clazz)
   {
      return supplyAsync(() -> builder.post(entity, clazz));
   }

   @Override
   public <T> CompletionStage<T> post(Entity<?> entity, GenericType<T> type)
   {
      return supplyAsync(() -> builder.post(entity, type));
   }

   @Override
   public CompletionStage<Response> delete()
   {
      return supplyAsync(() -> builder.delete());
   }

   @Override
   public <T> CompletionStage<T> delete(Class<T> responseType)
   {
      return supplyAsync(() -> builder.delete(responseType));
   }

   @Override
   public <T> CompletionStage<T> delete(GenericType<T> responseType)
   {
      return supplyAsync(() -> builder.delete(responseType));
   }

   @Override
   public CompletionStage<Response> head()
   {
      return supplyAsync(() -> builder.head());
   }

   @Override
   public CompletionStage<Response> options()
   {
      return supplyAsync(() -> builder.options());
   }

   @Override
   public <T> CompletionStage<T> options(Class<T> responseType)
   {
      return supplyAsync(() -> builder.options(responseType));
   }

   @Override
   public <T> CompletionStage<T> options(GenericType<T> responseType)
   {
      return supplyAsync(() -> builder.options(responseType));
   }

   @Override
   public CompletionStage<Response> trace()
   {
      return supplyAsync(() -> builder.trace());
   }

   @Override
   public <T> CompletionStage<T> trace(Class<T> responseType)
   {
      return supplyAsync(() -> builder.trace(responseType));
   }

   @Override
   public <T> CompletionStage<T> trace(GenericType<T> responseType)
   {
      return supplyAsync(() -> builder.trace(responseType));
   }

   @Override
   public CompletionStage<Response> method(String name)
   {
      return supplyAsync(() -> builder.method(name));
   }

   @Override
   public <T> CompletionStage<T> method(String name, Class<T> responseType)
   {
      return supplyAsync(() -> builder.method(name, responseType));
   }

   @Override
   public <T> CompletionStage<T> method(String name, GenericType<T> responseType)
   {
      return supplyAsync(() -> builder.method(name, responseType));
   }

   @Override
   public CompletionStage<Response> method(String name, Entity<?> entity)
   {
      return supplyAsync(() -> builder.method(name, entity));
   }

   @Override
   public <T> CompletionStage<T> method(String name, Entity<?> entity, Class<T> responseType)
   {
      return supplyAsync(() -> builder.method(name, entity, responseType));
   }

   @Override
   public <T> CompletionStage<T> method(String name, Entity<?> entity, GenericType<T> responseType)
   {
      return supplyAsync(() -> builder.method(name, entity, responseType));
   }

   public ExecutorService getExecutor()
   {
      return executor;
   }

   public CompletionStage<Response> patch(Entity<?> entity)
   {
      return supplyAsync(() -> builder.method(HttpMethod.PATCH, entity));
   }

   public <T> CompletionStage<T> patch(Entity<?> entity, Class<T> responseType)
   {
      return supplyAsync(() -> builder.method(HttpMethod.PATCH, entity, responseType));
   }

   public <T> CompletionStage<T> patch(Entity<?> entity, GenericType<T> responseType)
   {
      Supplier<T> supplier = () -> builder.method(HttpMethod.PATCH, entity, responseType);
      return supplyAsync(supplier);
   }

   private <T> CompletionStage<T> supplyAsync(Supplier<T> supplier) {
      CompletionStage<T> result;
      if (executor == null)
      {
         result = CompletableFuture.supplyAsync(supplier);
      }
      else
      {
         result = CompletableFuture.supplyAsync(supplier, executor);
      }
      return result.whenComplete((ignored, error) ->
      {
         System.out.println("in whencomplete");
         if (cleanUp != null)
         {// mstodo remove/do not commit!
            System.out.println("running clean-up");
            cleanUp.run();
         }
      });
   }

   @Override
   public void setCleanUp(Runnable asyncInvocationCleanUp) {
      System.out.println("setting clean up to: " + asyncInvocationCleanUp);
      Thread.dumpStack();
      this.cleanUp = asyncInvocationCleanUp;
   }
}
