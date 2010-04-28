/*
 * File   : $Source: /alkacon/cvs/opencms/src-modules/org/opencms/ade/containerpage/client/ui/Attic/A_CmsToolbarOptionButton.java,v $
 * Date   : $Date: 2010/04/28 13:03:39 $
 * Version: $Revision: 1.2 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (C) 2002 - 2009 Alkacon Software (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ade.containerpage.client.ui;

import org.opencms.ade.containerpage.client.CmsContainerpageHandler;
import org.opencms.ade.containerpage.client.draganddrop.CmsDragContainerElement;
import org.opencms.gwt.client.ui.I_CmsButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;

/**
 * Abstract button class implementing common methods of {@link org.opencms.ade.containerpage.client.ui.I_CmsToolbarButton} 
 * for container-page tool-bar buttons with element functions.<p>
 * 
 * @author Tobias Herrmann
 * 
 * @version $Revision: 1.2 $
 * 
 * @since 8.0.0
 */
public abstract class A_CmsToolbarOptionButton extends A_CmsToolbarButton {

    /** The click handler for all tool-bar buttons. */
    private static ClickHandler m_elementClickHandler = new ClickHandler() {

        /**
         * @see com.google.gwt.event.dom.client.ClickHandler#onClick(com.google.gwt.event.dom.client.ClickEvent)
         */
        public void onClick(ClickEvent event) {

            CmsElementOptionButton source = (CmsElementOptionButton)event.getSource();
            source.getToolbarButton().onElementClick(event, source.getDragElement());
        }
    };

    /**
     * Constructor.<p>
     * 
     * @param buttonData the button data
     * @param handler the container-page handler
     */
    protected A_CmsToolbarOptionButton(I_CmsButton.ButtonData buttonData, CmsContainerpageHandler handler) {

        super(buttonData, handler);
    }

    /**
     * Creates an element options button associated with this button and assigns the click-handler.<p>
     * 
     * @param element the element to create the button for
     * 
     * @return the created button
     */
    public CmsElementOptionButton createOptionForElement(CmsDragContainerElement element) {

        CmsElementOptionButton button = new CmsElementOptionButton(this, element);
        button.addClickHandler(m_elementClickHandler);
        button.setEnabled(hasPermissions(element));
        // TODO: if element option is not allowed, set reason as button title
        return button;
    }

    /**
     * Determines if the user has permissions to use the element function on the given element 
     * (if there are permissions to edit, delete etc.).<p>. 
     * 
     * @param element the element to check
     * 
     * @return <code>true</code> if the user has permissions
     */
    public abstract boolean hasPermissions(CmsDragContainerElement element);

    /**
     * Method is executed when the element option button is clicked.<p>
     * 
     * @param event the mouse event (stop propagation if appropriate)
     * @param element the element the option button is associated to
     */
    public abstract void onElementClick(ClickEvent event, CmsDragContainerElement element);

    /**
     * @see org.opencms.ade.containerpage.client.ui.I_CmsToolbarButton#onToolbarActivate()
     */
    public void onToolbarActivate() {

        showSingleElementOption(true);

    }

    /**
     * @see org.opencms.ade.containerpage.client.ui.I_CmsToolbarButton#onToolbarDeactivate()
     */
    public void onToolbarDeactivate() {

        showSingleElementOption(false);

    }
}
