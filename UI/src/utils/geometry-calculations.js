(function(geometrycalculator, undefined){
    geometrycalculator.getDistanceFromLine = function(line, point) {
        var nearest_point = geometrycalculator.nearestPointOnLine(line, point);
        var dx = nearest_point.x - point.x;
        var dy = nearest_point.y - point.y;
        return Math.sqrt(dx * dx + dy * dy);
    };

    geometrycalculator.nearestPointOnLine = function(line, point) {
        var length_of_side_x = line.end.x - line.start.x;
        var length_of_side_y = line.end.y - line.start.y;
        var sum_of_squared_sides = length_of_side_x * length_of_side_x + length_of_side_y * length_of_side_y;

        var apx = point.x - line.start.x;
        var apy = point.y - line.start.y;
        var t = (apx * length_of_side_x + apy * length_of_side_y) / sum_of_squared_sides;
        if(t < 0) { t = 0; }
        if(t > 1) { t = 1; }
        return { x: line.start.x + length_of_side_x * t, y: line.start.y + length_of_side_y * t };
    };

    geometrycalculator.findNearestLine = function(features, x, y) {
        var calculatedistance = function(item){
            return geometrycalculator.getDistanceFromLine(item, { x: x, y: y });
        };
        var fromFeatureVectorToLine = function(vector){
            var temp = {};
            var min_distance = 100000000.0;
            for(var i = 0; i < vector.geometry.components.length - 1; i++) {
                var start_point = vector.geometry.components[i];
                var end_point = vector.geometry.components[i + 1];
                var point_distance = calculatedistance({ start: start_point, end: end_point });
                if(point_distance < min_distance) {
                    min_distance = point_distance;
                    temp = { id: vector.id, roadLinkId: vector.attributes.roadLinkId, start: start_point, end: end_point, distance: point_distance };
                }
            }
            return temp;
        };

        return _.chain(features)
                .map(fromFeatureVectorToLine)
                .min('distance')
                .omit('distance')
                .value();
    };

    geometrycalculator.isInCircle = function(centerX, centerY, radius, x, y) {
        var squareDist = (centerX-x) * (centerX-x) + (centerY-y)*(centerY-y);
        return squareDist <= radius*radius;
    };

    geometrycalculator.getLineDirectionAngle = function(line) {
        return Math.atan2(line.start.y - line.end.y, line.start.x - line.end.y);
    };

    geometrycalculator.rad2deg = function(angleRad) {
        return angleRad * (180/Math.PI);
    };

    geometrycalculator.deg2rad = function(angleDeg) {
        return angleDeg * (Math.PI/180);
    };


}(window.geometrycalculator = window.geometrycalculator || {}));