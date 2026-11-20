package app.still.helper

import app.still.data.AppModel

interface AppFilterHelper {
    fun onAppFiltered(items:List<AppModel>)
}