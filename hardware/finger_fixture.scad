// Experimental dorsal middle-finger fixture. Units: mm.
// Nominal dimensions are candidate values, not fit/accuracy claims.
// Frame R: origin at fingertip stop; +Y distal, +Z dorsal, +X lateral.
$fn = 64;
finger_radius_mm = 8;
face_angle_degrees = 35;
wall_mm = 2;
support_spacing_mm = 18;
support_length_mm = 6;
marker_edge_mm = 10;
quiet_zone_mm = 2;
target_height_mm = 10;
face_edge = marker_edge_mm + 2*quiet_zone_mm;
outer_radius = finger_radius_mm + wall_mm;
target_base_z = finger_radius_mm + 3;
outer_half_width = face_edge/2 + face_edge*cos(face_angle_degrees);
side_bottom_z = target_height_mm - face_edge*sin(face_angle_degrees);

assert(finger_radius_mm >= 6 && finger_radius_mm <= 12, "Candidate radius out of experiment range");
assert(face_angle_degrees >= 20 && face_angle_degrees <= 45, "Candidate angle out of experiment range");
assert(side_bottom_z > 0, "Target sides must remain above mounting plane");

module dorsal_support(y) {
    translate([0,y,0]) {
        intersection() {
            difference() {
                rotate([90,0,0]) cylinder(r=outer_radius,h=support_length_mm,center=true);
                rotate([90,0,0]) cylinder(r=finger_radius_mm,h=support_length_mm+2,center=true);
            }
            translate([-outer_radius,-support_length_mm/2,0])
                cube([2*outer_radius,support_length_mm,outer_radius+1]);
        }
        // Strap slots; strap/liner choices require separate fit tests.
        for (sign=[-1,1]) {
            translate([sign*(outer_radius+1.5),0,1]) difference() {
                cube([7,support_length_mm,2],center=true);
                cube([3,support_length_mm-2,4],center=true);
            }
        }
    }
}

module three_face_target() {
    translate([0,-18,target_base_z]) rotate([90,0,0])
        linear_extrude(height=face_edge,center=true)
            polygon(points=[[-outer_half_width,0],[outer_half_width,0],
                [outer_half_width,side_bottom_z],[face_edge/2,target_height_mm],
                [-face_edge/2,target_height_mm],[-outer_half_width,side_bottom_z]]);
}

union() {
    dorsal_support(-8);
    dorsal_support(-8-support_spacing_mm);
    // Dorsal bridge connects both registration regions and the target mount.
    translate([-4,-30,outer_radius-1]) cube([8,31,2]);
    // Stop's proximal surface at Y=0 defines the nominal anatomical tip reference.
    translate([-4,0,-1]) cube([8,1,outer_radius+2]);
    three_face_target();
}

echo(candidate_tip_R_mm=[0,0,0]);
echo(central_face_center_R_mm=[0,-18,target_base_z+target_height_mm]);
echo(right_face_center_R_mm=[face_edge/2+face_edge/2*cos(face_angle_degrees),-18,
    target_base_z+target_height_mm-face_edge/2*sin(face_angle_degrees)]);
echo(left_face_center_R_mm=[-face_edge/2-face_edge/2*cos(face_angle_degrees),-18,
    target_base_z+target_height_mm-face_edge/2*sin(face_angle_degrees)]);
