/**
 * Waffle (https://github.com/dblock/waffle)
 *
 * Copyright (c) 2010 - 2014 Application Security, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Application Security, Inc.
 */
package waffle.jaas;

import java.io.Serializable;
import java.security.Principal;

/**
 * User Principal.
 * 
 * @author dblock[at]dblock[dot]org
 */
public class UserPrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;
    private String            fqn;

    /**
     * A user principal.
     * 
     * @param newFqn
     *            Fully qualified username.
     */
    public UserPrincipal(final String newFqn) {
        this.fqn = newFqn;
    }

    /**
     * Fully qualified username.
     */
    @Override
    public String getName() {
        return this.fqn;
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) {
            return true;
        }

        if (o instanceof UserPrincipal) {
            return getName().equals(((UserPrincipal) o).getName());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
