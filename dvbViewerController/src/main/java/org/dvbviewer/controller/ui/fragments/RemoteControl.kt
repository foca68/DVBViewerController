/*
 * Copyright © 2013 dvbviewer-controller Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.dvbviewer.controller.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.apache.commons.lang3.StringUtils
import org.dvbviewer.controller.R
import org.dvbviewer.controller.databinding.FragmentRemoteControlBinding
import org.dvbviewer.controller.ui.base.AbstractRemote
import org.dvbviewer.controller.utils.ActionID

class RemoteControl : AbstractRemote() {

    private var _binding: FragmentRemoteControlBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRemoteControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        inititalize()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun inititalize() {
        binding.btnMoveDown.setOnClickListener(this)
        binding.btnMoveUp.setOnClickListener(this)
        binding.btnOK.setOnClickListener(this)
        binding.btnMoveLeft.setOnClickListener(this)
        binding.btnMoveRight.setOnClickListener(this)
        binding.btnBack.setOnClickListener(this)
        binding.btnMenu.setOnClickListener(this)
        binding.btnVideos.setOnClickListener(this)
        binding.btnStepForward.setOnClickListener(this)
        binding.btnStepBack.setOnClickListener(this)
        binding.btnText.setOnClickListener(this)
        binding.btnPause.setOnClickListener(this)
        binding.btnStop.setOnClickListener(this)
        binding.btnRed.setOnClickListener(this)
        binding.btnGreen.setOnClickListener(this)
        binding.btnYellow.setOnClickListener(this)
        binding.btnBlue.setOnClickListener(this)
    }

    override fun toString(): String {
        return "Remote"
    }

    override fun getCommand(v: View): String {
        return when (v.id) {
            R.id.btnMoveUp -> ActionID.CMD_MOVE_UP
            R.id.btnMoveDown -> ActionID.CMD_MOVE_DOWN
            R.id.btnMoveRight -> ActionID.CMD_MOVE_RIGHT
            R.id.btnMoveLeft -> ActionID.CMD_MOVE_LEFT
            R.id.btnOK -> ActionID.CMD_SELECT_ITEM
            R.id.btnBack -> ActionID.CMD_PREVIOUS_MENU
            R.id.btnMenu -> ActionID.CMD_SHOW_OSD
            R.id.btnVideos -> ActionID.CMD_SHOW_VIDEO
            R.id.btnStepForward -> ActionID.CMD_STEP_FORWARD
            R.id.btnStepBack -> ActionID.CMD_STEP_BACK
            R.id.btnText -> ActionID.CMD_SHOW_TELETEXT
            R.id.btnPause -> ActionID.CMD_PAUSE
            R.id.btnStop -> ActionID.CMD_STOP
            R.id.btnRed -> ActionID.CMD_RED
            R.id.btnGreen -> ActionID.CMD_GREEN
            R.id.btnYellow -> ActionID.CMD_YELLOW
            R.id.btnBlue -> ActionID.CMD_BLUE
            else -> StringUtils.EMPTY
        }
    }

}
