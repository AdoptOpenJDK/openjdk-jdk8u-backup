/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.lwawt.macosx;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.event.EventListenerList;

/**
 * <P>{@code AccessibilityEventMonitor} implements a PropertyChange listener
 * on every UI object that implements interface {@code Accessible} in the Java
 * Virtual Machine.  The events captured by these listeners are made available
 * through listeners supported by {@code AccessibilityEventMonitor}.
 * With this, all the individual events on each of the UI object
 * instances are funneled into one set of PropertyChange listeners.
 *
 * This code is a subset of com.sun.java.accessibility.util.AccessibilityEventMonitor
 * which resides in module jdk.accessibility.  Due to modularization the code in
 * this package, java.desktop, can not be dependent on code in jdk.accessibility.
 */

class AccessibilityEventMonitor {

    /**
     * The current list of registered {@link java.beans.PropertyChangeListener
     * PropertyChangeListener} classes.
     *
     * @see #addPropertyChangeListener
     */
    private static final EventListenerList listenerList =
        new EventListenerList();


    /**
     * The actual listener that is installed on the component instances.
     * This listener calls the other registered listeners when an event
     * occurs.  By doing things this way, the actual number of listeners
     * installed on a component instance is drastically reduced.
     */
    private static final AccessibilityEventListener accessibilityListener =
        new AccessibilityEventListener();

    /**
     * Adds the specified listener to receive all PropertyChange events on
     * each UI object instance in the Java Virtual Machine as they occur.
     * <P>Note: This listener is automatically added to all component
     * instances created after this method is called.  In addition, it
     * is only added to UI object instances that support this listener type.
     *
     * @param l the listener to add
     * @param a  the Accessible object to add the PropertyChangeListener to
     */

    static void addPropertyChangeListener(PropertyChangeListener l, Accessible a) {
        if (listenerList.getListenerCount(PropertyChangeListener.class) == 0) {
            accessibilityListener.installListeners(a);
        }
        listenerList.add(PropertyChangeListener.class, l);
    }

    /**
     * AccessibilityEventListener is the class that does all the work for
     * AccessibilityEventMonitor.  It is not intended for use by any other
     * class except AccessibilityEventMonitor.
     */

    private static class AccessibilityEventListener implements PropertyChangeListener {

        /**
         * Installs PropertyChange listeners to the Accessible object, and its
         * children (so long as the object isn't of TRANSIENT state).
         *
         * @param a the Accessible object to add listeners to
         */
        private void installListeners(Accessible a) {
            installListeners(a.getAccessibleContext());
        }

        /**
         * Installs PropertyChange listeners to the AccessibleContext object,
         * and its * children (so long as the object isn't of TRANSIENT state).
         *
         * @param ac the AccessibleContext to add listeners to
         */
        private void installListeners(AccessibleContext ac) {

            if (ac != null) {
                AccessibleStateSet states = ac.getAccessibleStateSet();
                if (!states.contains(AccessibleState.TRANSIENT)) {
                    ac.addPropertyChangeListener(this);
                    /*
                     * Don't add listeners to transient children. Components
                     * with transient children should return an AccessibleStateSet
                     * containing AccessibleState.MANAGES_DESCENDANTS. Components
                     * may not explicitly return the MANAGES_DESCENDANTS state.
                     * In this case, don't add listeners to the children of
                     * lists, tables and trees.
                     */
                    AccessibleStateSet set = ac.getAccessibleStateSet();
                    if (set.contains(AccessibleState.MANAGES_DESCENDANTS)) {
                        return;
                    }
                    AccessibleRole role = ac.getAccessibleRole();
                    if ( role == AccessibleRole.LIST ||
                         role == AccessibleRole.TREE ) {
                        return;
                    }
                    if (role == AccessibleRole.TABLE) {
                        // handle Oracle tables containing tables
                        Accessible child = ac.getAccessibleChild(0);
                        if (child != null) {
                            AccessibleContext ac2 = child.getAccessibleContext();
                            if (ac2 != null) {
                                role = ac2.getAccessibleRole();
                                if (role != null && role != AccessibleRole.TABLE) {
                                    return;
                                }
                            }
                        }
                    }
                    int count = ac.getAccessibleChildrenCount();
                    for (int i = 0; i < count; i++) {
                        Accessible child = ac.getAccessibleChild(i);
                        if (child != null) {
                            installListeners(child);
                        }
                    }
                }
            }
        }

        /**
         * Removes PropertyChange listeners for the given Accessible object,
         * its children (so long as the object isn't of TRANSIENT state).
         *
         * @param a the Accessible object to remove listeners from
         */
        private void removeListeners(Accessible a) {
            removeListeners(a.getAccessibleContext());
        }

        /**
         * Removes PropertyChange listeners for the given AccessibleContext
         * object, its children (so long as the object isn't of TRANSIENT
         * state).
         *
         * @param a the Accessible object to remove listeners from
         */
        private void removeListeners(AccessibleContext ac) {

            if (ac != null) {
                // Listeners are not added to transient components.
                AccessibleStateSet states = ac.getAccessibleStateSet();
                if (!states.contains(AccessibleState.TRANSIENT)) {
                    ac.removePropertyChangeListener(this);
                    /*
                     * Listeners are not added to transient children. Components
                     * with transient children should return an AccessibleStateSet
                     * containing AccessibleState.MANAGES_DESCENDANTS. Components
                     * may not explicitly return the MANAGES_DESCENDANTS state.
                     * In this case, don't remove listeners from the children of
                     * lists, tables and trees.
                     */
                    if (states.contains(AccessibleState.MANAGES_DESCENDANTS)) {
                        return;
                    }
                    AccessibleRole role = ac.getAccessibleRole();
                    if ( role == AccessibleRole.LIST ||
                         role == AccessibleRole.TABLE ||
                         role == AccessibleRole.TREE ) {
                        return;
                    }
                    int count = ac.getAccessibleChildrenCount();
                    for (int i = 0; i < count; i++) {
                        Accessible child = ac.getAccessibleChild(i);
                        if (child != null) {
                            removeListeners(child);
                        }
                    }
                }
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            // propogate the event
            Object[] listeners =
                AccessibilityEventMonitor.listenerList.getListenerList();
            for (int i = listeners.length-2; i>=0; i-=2) {
                if (listeners[i]==PropertyChangeListener.class) {
                    ((PropertyChangeListener)listeners[i+1]).propertyChange(e);
                }
            }

            // handle childbirth/death
            String name = e.getPropertyName();
            if (name.compareTo(AccessibleContext.ACCESSIBLE_CHILD_PROPERTY) == 0) {
                Object oldValue = e.getOldValue();
                Object newValue = e.getNewValue();

                if ((oldValue == null) ^ (newValue == null)) { // one null, not both
                    if (oldValue != null) {
                        // this Accessible is a child that's going away
                        if (oldValue instanceof Accessible) {
                            Accessible a = (Accessible) oldValue;
                            removeListeners(a.getAccessibleContext());
                        } else if (oldValue instanceof AccessibleContext) {
                            removeListeners((AccessibleContext) oldValue);
                        }
                    } else if (newValue != null) {
                        // this Accessible is a child was just born
                        if (newValue instanceof Accessible) {
                            Accessible a = (Accessible) newValue;
                            installListeners(a.getAccessibleContext());
                        } else if (newValue instanceof AccessibleContext) {
                            installListeners((AccessibleContext) newValue);
                        }
                    }
                } else {
                    System.out.println("ERROR in usage of PropertyChangeEvents for: " + e.toString());
                }
            }
        }
    }
}
