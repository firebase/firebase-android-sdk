// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <android/configuration.h>
#include <android/sensor.h>

#include "crashlytics/detail/scoped_writer.h"
#include "crashlytics/handler/detail/system_info.h"
#include "crashlytics/handler/device_info.h"
#include "crashlytics/handler/maps.h"
#include "crashlytics/detail/memory/allocate.h"

void google::crashlytics::handler::write_device_info(const google::crashlytics::handler::detail::context& handler_context, int fd)
{
    auto memory  = detail::memory_statistics();
    auto storage = detail::internal_storage_statistics();

    auto proximity_sensor_enabled = handler_context.sensor_manager != nullptr && ASensorManager_getDefaultSensor(
        handler_context.sensor_manager,
        ASENSOR_TYPE_PROXIMITY
    );

    // It is unsafe to fetch the orientation of the device at crash time via the
    // native API. Doing so causes some apps to hang indeffinitely. At this time
    // we will set the orientation to ne unknown.
    auto orientation = static_cast<uint64_t>(ACONFIGURATION_ORIENTATION_ANY);
    auto battery     = static_cast<uint64_t>(detail::battery_capacity());

    using google::crashlytics::detail::scoped_writer;

    scoped_writer writer(fd);
    scoped_writer::wrapped object('{', '}', scoped_writer::None, writer);

    writer.write("orientation",                 orientation,                scoped_writer::Comma);
    writer.write("total_physical_memory",       memory.first,               scoped_writer::Comma);
    writer.write("total_internal_storage",      storage.first,              scoped_writer::Comma);
    writer.write("available_physical_memory",   memory.second,              scoped_writer::Comma);
    writer.write("available_internal_storage",  storage.second,             scoped_writer::Comma);
    writer.write("battery",                     battery,                    scoped_writer::Comma);
    writer.write("proximity_enabled",           proximity_sensor_enabled,   scoped_writer::None);
}

void google::crashlytics::handler::write_binary_libs(const google::crashlytics::handler::detail::context& handler_context, int fd)
{
    using google::crashlytics::detail::scoped_writer;

    scoped_writer writer(fd);

    detail::maps_entries(handler_context.pid, [&](const char* buffer, std::size_t buffer_size) {
        writer.write(buffer, buffer_size);
    });
}
