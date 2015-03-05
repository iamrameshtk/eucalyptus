/*************************************************************************
 * Copyright 2009-2015 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/
package com.eucalyptus.util;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.eucalyptus.auth.principal.AccountFullName;
import com.eucalyptus.component.ComponentId;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.Topology;
import com.eucalyptus.util.async.AsyncRequests;
import com.eucalyptus.util.concurrent.ListenableFuture;
import com.google.common.base.Objects;
import edu.ucsb.eucalyptus.msgs.BaseMessage;

/**
 *
 */
public class DispatchingClient<MT extends BaseMessage,CT extends ComponentId> {
  @Nullable
  private final String userId;
  private final AccountFullName accountFullName;
  private final Class<CT> componentIdClass;
  private ServiceConfiguration configuration;

  public DispatchingClient( @Nullable final String userId,
                            @Nonnull  final Class<CT> componentIdClass ) {
    this.userId = userId;
    this.accountFullName = null;
    this.componentIdClass = componentIdClass;
  }

  public DispatchingClient( @Nullable final AccountFullName accountFullName,
                            @Nonnull  final Class<CT> componentIdClass ) {
    this.userId = null;
    this.accountFullName = accountFullName;
    this.componentIdClass = componentIdClass;
  }

  public DispatchingClient( @Nonnull final Class<CT> componentIdClass ) {
    this( (String)null, componentIdClass );
  }

  public void init() throws DispatchingClientException {
    try {
      this.configuration = Topology.lookup( componentIdClass );
    } catch ( final NoSuchElementException e ) {
      throw new DispatchingClientException( e );
    }
  }

  public <REQ extends MT,RES extends MT>
   void dispatch( final REQ request, final Callback.Checked<RES> callback ) {
    dispatch( request, callback, null );
  }

  public <REQ extends MT,RES extends MT>
   void dispatch( final REQ request,
                 final Callback.Checked<RES> callback,
                 @Nullable final Runnable then ) {
    request.setUserId( userId != null ? userId : accountFullName != null ? accountFullName.getAccountNumber( ) : null );
    request.markPrivileged( );
    try {
      final ListenableFuture<RES> future =
          AsyncRequests.dispatch( configuration, request );
      future.addListener( new Runnable() {
        @Override
        public void run() {
          try {
            callback.fire( future.get() );
          } catch ( InterruptedException e ) {
            // future is complete so this can't happen
            callback.fireException( e );
          } catch ( ExecutionException e ) {
            callback.fireException( e.getCause() );
          } finally {
            if ( then != null ) then.run();
          }
        }
      } );
    } catch ( final Exception e ) {
      try {
        callback.fireException( e );
      } finally{
        if ( then != null ) then.run();
      }
    }
  }

  public static final class DispatchingClientException extends Exception {
    private static final long serialVersionUID = 1L;

    public DispatchingClientException( final String message ) {
      super( message );
    }

    public DispatchingClientException( final String message,
                                      final Throwable cause ) {
      super( message, cause );
    }

    public DispatchingClientException( final Throwable cause ) {
      super( cause );
    }
  }
}
