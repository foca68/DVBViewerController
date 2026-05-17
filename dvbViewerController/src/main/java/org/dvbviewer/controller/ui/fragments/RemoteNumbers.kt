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
import org.dvbviewer.controller.databinding.FragmentRemoteNumbersBinding
import org.dvbviewer.controller.ui.base.AbstractRemote
import org.dvbviewer.controller.utils.ActionID


class RemoteNumbers : AbstractRemote() {

    private var _binding: FragmentRemoteNumbersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRemoteNumbersBinding.inflate(inflater, container, false)
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
        binding.btnOne.setOnClickListener(this)
        binding.btnTwo.setOnClickListener(this)
        binding.btnThree.setOnClickListener(this)
        binding.btnFour.setOnClickListener(this)
        binding.btnFive.setOnClickListener(this)
        binding.btnSix.setOnClickListener(this)
        binding.btnSeven.setOnClickListener(this)
        binding.btnEight.setOnClickListener(this)
        binding.btnNine.setOnClickListener(this)
        binding.btnZero.setOnClickListener(this)
        binding.btnStepBack.setOnClickListener(this)
        binding.btnStepForward.setOnClickListener(this)
    }

    override fun toString(): String {
        return "Remote"
    }

    override fun getCommand(v: View): String {
        return when (v.id) {
            R.id.btnOne -> ActionID.CMD_REMOTE_1
            R.id.btnTwo -> ActionID.CMD_REMOTE_2
            R.id.btnThree -> ActionID.CMD_REMOTE_3
            R.id.btnFour -> ActionID.CMD_REMOTE_4
            R.id.btnFive -> ActionID.CMD_REMOTE_5
            R.id.btnSix -> ActionID.CMD_REMOTE_6
            R.id.btnSeven -> ActionID.CMD_REMOTE_7
            R.id.btnEight -> ActionID.CMD_REMOTE_8
            R.id.btnNine -> ActionID.CMD_REMOTE_9
            R.id.btnZero -> ActionID.CMD_REMOTE_0
            R.id.btnStepBack -> ActionID.CMD_MOVE_LEFT
            R.id.btnStepForward -> ActionID.CMD_MOVE_RIGHT
            else -> StringUtils.EMPTY
        }
    }

}
