# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

set(Glog_FOUND TRUE)
set(Glog_LIBRARY glog)
get_target_property(Glog_INCLUDE_PATH glog::glog INTERFACE_INCLUDE_DIRECTORIES)
